package com.youtoo
package mail
package service

import zio.*
import zio.prelude.*

import com.youtoo.mail.model.*
import com.youtoo.job.model.*
import com.youtoo.job.service.*

import com.youtoo.lock.*

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*
import zio.stream.*
import com.youtoo.mail.integration.*

trait SyncService {
  def sync(id: MailAccount.Id, options: SyncOptions): ZIO[Scope & Tracing, Throwable, Unit]
}

object SyncService {
  val MailSync = Job.Tag("MailSync")

  inline def sync(id: MailAccount.Id): ZIO[
    Scope & SyncService & Tracing,
    Throwable,
    Unit,
  ] =
    for {
      options <- ZIO.config[SyncOptions]
      _ <- ZIO.serviceWithZIO[SyncService](_.sync(id, options))
    } yield ()

  inline def sync(id: MailAccount.Id, options: SyncOptions): ZIO[
    Scope & SyncService & Tracing,
    Throwable,
    Unit,
  ] =
    ZIO.serviceWithZIO[SyncService](_.sync(id, options))

  def live(): ZLayer[
    LockManager & MailClient & MailService & JobService & Tracing,
    Nothing,
    SyncService,
  ] =
    ZLayer.fromFunction {
      (
        mailClient: MailClient,
        mailService: MailService,
        jobService: JobService,
        lockManager: LockManager,
        tracing: Tracing,
      ) =>
        new SyncServiceLive(
          mailClient,
          mailService,
          jobService,
          lockManager,
        ).traced(tracing)
    }

  class SyncServiceLive(
    mailClient: MailClient,
    mailService: MailService,
    jobService: JobService,
    lockManager: LockManager,
  ) extends SyncService { self =>

    def sync(id: MailAccount.Id, options: SyncOptions): ZIO[Scope & Tracing, Throwable, Unit] =
      mailService.loadAccount(id).flatMap {
        case None => Log.error("Account not found")
        case Some(account) =>
          scopedTask {

            withLock(account) {
              for {
                state <- mailService.loadState(account.id)
                _ <- state match {
                  case Some(mail) => performSync(account, mail, options)
                  case None => Log.error("Mail state not found")
                }
              } yield ()
            }
          }
      }

    def scopedTask[R, E, A](task: ZIO[R & Scope, E, A]): ZIO[R & Scope, E, A] =
      ZIO.acquireRelease {
        ZIO.scoped(task).fork
      } { case (fiber) =>
        fiber.join.ignoreLogged
      }.flatMap { case fiber =>
        fiber.join
      }

    private def withLock(account: MailAccount)(
      action: => ZIO[Scope & Tracing, Throwable, Unit],
    ): ZIO[Scope & Tracing, Throwable, Unit] =
      val lock = lockManager.aquireScoped(account.lock)
      ZIO.ifZIO(lock)(
        onTrue = action,
        onFalse = Log.info(s"Sync already in progress for account"),
      )

    private def performSync(
      account: MailAccount,
      mail: Mail,
      options: SyncOptions,
    ): ZIO[Scope & Tracing, Throwable, Unit] =
      ZIO.uninterruptibleMask { restore =>
        for {
          timestamp <- Timestamp.gen
          jobId <- Job.Id.gen
          _ <- jobService.startJob(id = jobId, timestamp, total = JobMeasurement.Variable(), tag = MailSync)
          _ <- mailService.startSync(accountKey = account.id, labels = MailLabels.All(), timestamp, jobId)
          reason <- fetchAndRecordMails(options, account, mail.cursor.map(_.token), jobId, restore).fold(
            success =
              cancelled => if cancelled then Job.CompletionReason.Cancellation() else Job.CompletionReason.Success(),
            failure = e => Job.CompletionReason.Failure(e.getMessage),
          )
          endTimestamp <- Timestamp.gen
          _ <- mailService.completeSync(accountKey = account.id, endTimestamp, jobId)
          _ <- jobService.completeJob(id = jobId, timestamp = endTimestamp, reason = reason)
          _ <- reason match {
            case Job.CompletionReason.Success() => Log.info("Sync complete")
            case Job.CompletionReason.Cancellation() => Log.info("Sync cancelled")
            case Job.CompletionReason.Failure(m) => Log.error(s"Sync failed: $m")
          }
        } yield ()
      }

    private def fetchAndRecordMails(
      options: SyncOptions,
      account: MailAccount,
      token: Option[MailToken],
      jobId: Job.Id,
      restore: ZIO.InterruptibilityRestorer,
    ): ZIO[Scope & Tracing, Throwable, Boolean] =
      (
        Ref.make(false) <&> Timestamp.gen
      ) flatMap { case (cancelledRef, timestamp) =>
        val state = SyncState(started = timestamp, token = token, iterations = 0)

        ZStream
          .unfoldZIO(state) { state =>
            Log.debug(s"Begin fetch for account") *> restore(
              options.retry(
                mailClient.fetchMails(account.id, state.token, Set()),
              ),
            ).flatMap {
              case None => ZIO.none
              case Some((mailKeys, nextToken)) =>
                for {
                  _ <- Log.debug(s"Fetched ${mailKeys.size} mails for account")
                  timestamp <- Timestamp.gen
                  _ <- mailService.recordSynced(accountKey = account.id, timestamp, mailKeys, nextToken, jobId)
                  expired = state.isExpired(options, timestamp)
                  cancelled <- if !expired then jobService.isCancelled(jobId) else ZIO.succeed(false)
                  _ <- Log.info(s"Sync cancelled for account") when cancelled
                  _ <- Log.info(s"Sync expired for account") when expired
                  _ <- cancelledRef.set(true) when cancelled
                } yield
                  if cancelled || expired then None
                  else Some((), state.next(nextToken))
            }
          }
          .runDrain *> cancelledRef.get
      }

    def traced(tracing: Tracing): SyncService = new SyncService {

      def sync(id: MailAccount.Id, options: SyncOptions): ZIO[Scope & Tracing, Throwable, Unit] =
        self.sync(id, options) @@ tracing.aspects.span(
          "SyncService.sync",
          attributes = Attributes(Attribute.long("accountId", id.asKey.value)),
        )

    }

  }

  case class SyncState(started: Timestamp, token: Option[MailToken], iterations: Int)

  object SyncState {
    extension (s: SyncState)
      inline def next(token: MailToken): SyncState = s.copy(iterations = s.iterations + 1, token = Some(token))

    extension (s: SyncState)
      inline def isExpired(options: SyncOptions, timestamp: Timestamp): Boolean =
        val durationExpired = options.maxDuration match {
          case None => false
          case Some(duration) => duration.toMillis < (timestamp.value - s.started.value)
        }

        val iterationsExpired = options.maxIterations match {
          case None => false
          case Some(iterations) => iterations < s.iterations
        }

        durationExpired || iterationsExpired

  }

}

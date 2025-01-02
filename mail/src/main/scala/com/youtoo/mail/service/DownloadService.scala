package com.youtoo
package mail
package service

import cats.implicits.*

import zio.interop.catz.core.*

import zio.*
import zio.stream.*
import zio.prelude.*
import zio.jdbc.*

import com.youtoo.mail.model.*
import com.youtoo.mail.integration.*
import com.youtoo.mail.store.*
import com.youtoo.job.model.*
import com.youtoo.job.service.*
import com.youtoo.cqrs.*
import com.youtoo.postgres.*
import com.youtoo.lock.*

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*

import com.youtoo.mail.integration.internal.GmailSupport

import java.time.temporal.ChronoUnit

trait DownloadService {
  def download(accountKey: MailAccount.Id, options: DownloadOptions): ZIO[Scope & Tracing, Throwable, Unit]
}

object DownloadService {
  val DownloadSync = Job.Tag("DownloadSync")
  val BatchSize = 50

  inline def download(accountKey: MailAccount.Id): ZIO[Scope & DownloadService & Tracing, Throwable, Unit] =
    for {
      options <- ZIO.config[DownloadOptions]
      _ <- ZIO.serviceWithZIO[DownloadService](_.download(accountKey, options))
    } yield ()

  inline def download(
    accountKey: MailAccount.Id,
    options: DownloadOptions,
  ): ZIO[Scope & DownloadService & Tracing, Throwable, Unit] =
    ZIO.serviceWithZIO(_.download(accountKey, options))

  def live(): ZLayer[
    ZConnectionPool & LockManager & JobService & MailClient & MailService & DownloadCQRS & MailEventStore & Tracing,
    Throwable,
    DownloadService,
  ] =
    ZLayer.fromFunction {
      (
        pool: ZConnectionPool,
        lockManager: LockManager,
        jobService: JobService,
        client: MailClient,
        mailService: MailService,
        downloadCQRS: DownloadCQRS,
        mailStore: MailEventStore,
        tracing: Tracing,
      ) =>
        new DownloadServiceLive(
          pool,
          lockManager,
          jobService,
          client,
          mailService,
          downloadCQRS,
          mailStore,
        ).traced(tracing)
    }

  class DownloadServiceLive(
    pool: ZConnectionPool,
    lockManager: LockManager,
    jobService: JobService,
    client: MailClient,
    mailService: MailService,
    cqrs: DownloadCQRS,
    mailStore: MailEventStore,
  ) extends DownloadService { self =>

    def download(accountKey: MailAccount.Id, options: DownloadOptions): ZIO[Scope & Tracing, Throwable, Unit] =
      mailService.loadAccount(accountKey).flatMap {
        case None => ZIO.fail(new IllegalArgumentException(s"Account with key $accountKey not found"))
        case Some(account) =>
          scopedTask { interruption =>
            withLock(account) {
              mailService.loadDownloadState(accountKey) flatMap {
                case None => Log.error("Download state not found")
                case Some(download) if !download.authorization.isGranted() => Log.error("Mail account not authorized")
                case Some(download) =>
                  for {
                    timestamp <- Timestamp.gen
                    jobId <- Job.Id.gen
                    _ <- jobService.startJob(jobId, timestamp, JobMeasurement.Variable(), DownloadSync)
                    reason <- fetchAndSaveMails(
                      options,
                      account,
                      download.lastVersion,
                      jobId,
                      interruption,
                    )
                      .foldZIO(
                        success = cancelled =>
                          ZIO.succeed(
                            if cancelled then Job.CompletionReason.Cancellation() else Job.CompletionReason.Success(),
                          ),
                        failure = e =>
                          account.accountType match {
                            case AccountType.Gmail =>
                              val reason = Job.CompletionReason.Failure(Option(e.getMessage))

                              if GmailSupport.isAuthorizationRevoked(e) then
                                for {
                                  _ <- Log.error(s"Mail account token revoked: ${account.id}")
                                  timestamp <- Timestamp.gen
                                  _ <- mailService.revokeAuthorization(account.id, timestamp)

                                } yield reason
                              else ZIO.succeed(reason)
                          },
                      )
                    completionTimestamp <- Timestamp.gen
                    _ <- jobService.completeJob(jobId, completionTimestamp, reason = reason)
                    _ <- reason match {
                      case Job.CompletionReason.Success() => Log.info("Download complete")
                      case Job.CompletionReason.Cancellation() => Log.info("Download cancelled")
                      case Job.CompletionReason.Failure(m) =>
                        Log.error(s"""Download failed: ${m.getOrElse("<unknown>")}""")
                    }
                  } yield ()
              }
            }
          }
      }

    private def fetchAndSaveMails(
      options: DownloadOptions,
      account: MailAccount,
      offset: Option[Version],
      jobId: Job.Id,
      interruption: Ref[Boolean],
    ): ZIO[Scope & Tracing, Throwable, Boolean] =
      ZIO.uninterruptibleMask { restore =>
        (
          Ref.make(false) <&> Timestamp.gen
        ) flatMap { case (cancelledRef, timestamp) =>
          val init = DownloadState(timestamp, offset, 1)

          ZStream
            .unfoldZIO(init) { state =>
              val fo = state.version match {
                case None => FetchOptions().limit(1L).asc()
                case Some(version) => FetchOptions().offset(version.asKey).limit(1L).asc()
              }

              for {
                changes <- restore(
                  mailStore
                    .readEvents(account.id.asKey, PersistenceQuery.ns(MailEvent.NS.MailSynced), fo)
                    .atomically
                    .provideEnvironment(ZEnvironment(pool)),
                )

                keys = changes.fold(List.empty)(_.toList.flatMap { change =>
                  change.payload match {
                    case MailEvent.MailSynced(_, mailKeys, _, _) => mailKeys.toList
                    case _ => List.empty
                  }
                })

                nextVersion = changes.map(_.toList.maxBy(_.version).version)

                timestamp <- Timestamp.gen
                _ <- Log.debug(s"Processing ${keys.size} mails")

                _ <- ZStream
                  .fromIterable(keys)
                  .mapZIO(mailId => restore(client.loadMessage(account.id, mailId)))
                  .grouped(BatchSize)
                  .mapZIO { batch =>
                    val mails = batch.sequence flatMap NonEmptyList.fromIterableOption
                    mails match {
                      case Some(nel) => mailService.saveMails(nel)
                      case None => ZIO.unit
                    }
                  }
                  .runDrain

                _ <- nextVersion match {
                  case None => ZIO.unit
                  case Some(version) => cqrs.add(account.id.asKey, DownloadCommand.RecordDownload(version, jobId))
                }

                interrupted <- interruption.get
                nextState = nextVersion.fold(state)(version => state.next(version))
                expired = nextState.isExpired(options, timestamp)
                cancelled <- if !expired && !interrupted then jobService.isCancelled(jobId) else ZIO.succeed(false)
                _ <- Log.info(s"Download interrupted for account") when interrupted
                _ <- Log.info(s"Download cancelled for account") when cancelled
                _ <- Log.info(s"Download expired for account") when expired
                _ <- cancelledRef.set(true) when cancelled

              } yield
                (
                  if cancelled || expired || interrupted then None
                  else Some(((), nextState))
                )

            }
            .runDrain *> cancelledRef.get

        }

      }

    def scopedTask[R, E, A](task: Ref[Boolean] => ZIO[R & Scope, E, A]): ZIO[R & Tracing & Scope, E, A] =
      ZIO.acquireRelease {
        for {
          interruption <- Ref.make(false)
          fiber <- ZIO.scoped(task(interruption)).fork
        } yield (interruption, fiber)
      } { case (interruption, fiber) =>
        for {
          _ <- Log.error("Download interruption requested")
          _ <- interruption.set(true).fork

          _ <- fiber.join
            .timeoutFail(new IllegalStateException("timout"))(Duration(30L, ChronoUnit.SECONDS))
            .ignoreLogged

        } yield ()
      }.flatMap { case (_, fiber) => fiber.join }

    private def withLock(account: MailAccount)(
      action: => ZIO[Scope & Tracing, Throwable, Unit],
    ): ZIO[Scope & Tracing, Throwable, Unit] =
      val lock = lockManager.acquireScoped(account.lock)
      ZIO.ifZIO(lock)(
        onTrue = action,
        onFalse = Log.info(s"Download already in progress for account"),
      )

    def traced(tracing: Tracing): DownloadService = new DownloadService {

      def download(accountKey: MailAccount.Id, options: DownloadOptions): ZIO[Scope & Tracing, Throwable, Unit] =
        self.download(accountKey, options) @@ tracing.aspects.span(
          "DownloadService.download",
          attributes = Attributes(Attribute.long("accountId", accountKey.asKey.value)),
        )
    }

  }

  case class DownloadState(started: Timestamp, version: Option[Version], iterations: Int)

  object DownloadState {
    extension (s: DownloadState)
      inline def next(version: Version): DownloadState =
        s.copy(iterations = s.iterations + 1, version = Some(version))

    extension (s: DownloadState)
      inline def isExpired(options: DownloadOptions, timestamp: Timestamp): Boolean =
        val durationExpired = options.maxDuration match {
          case None => false
          case Some(duration) => duration.toMillis < (timestamp.value - s.started.value)
        }

        val iterationsExpired = options.maxIterations match {
          case None => false
          case Some(iterations) => iterations <= s.iterations
        }

        durationExpired || iterationsExpired

  }

}

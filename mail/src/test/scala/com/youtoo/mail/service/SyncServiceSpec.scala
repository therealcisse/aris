package com.youtoo
package mail
package service

import zio.*
import zio.prelude.*
import zio.mock.*
import zio.test.*
import zio.test.Assertion
import zio.mock.Expectation

import com.youtoo.mail.model.*
import com.youtoo.mail.integration.*
import com.youtoo.lock.*
import com.youtoo.lock.repository.*
import com.youtoo.job.service.*
import com.youtoo.job.model.*
import com.youtoo.postgres.*
import com.youtoo.mail.client.*

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.jdbc.ZConnectionPool

object SyncServiceSpec extends MockSpecDefault, TestSupport {

  def spec = suite("SyncService")(
    test("Account Not Found") {
      val mockEnv: ZLayer[Any, Throwable, MailClient & MailService & JobService & LockManager] = MailServiceMock
        .LoadAccount(
          assertion = Assertion.equalTo(MailAccount.Id(Key(1))),
          result = Expectation.value(None),
        )
        .toLayer ++ MailClientMock.empty ++ LockManagerMock.empty ++ JobServiceMock.empty

      val effect = SyncService.sync(MailAccount.Id(Key(1)))

      val r = ZIO.scoped(effect) `as` assertCompletes

      r.provideSomeLayer[Tracing](mockEnv >>> SyncService.live())
    },
    test("Mail State Not Found") {
      check(mailAccountGen) { account =>
        val mockEnv = (MailServiceMock.LoadAccount(
          assertion = Assertion.equalTo(account.id),
          result = Expectation.value(Some(account)),
        ) ++ LockManagerMock.AcquireScoped(
          assertion = Assertion.equalTo(account.lock),
          result = Expectation.value(true),
        ) ++ MailServiceMock.LoadState(
          assertion = Assertion.equalTo(account.id),
          result = Expectation.value(None),
        )).toLayer ++ MailClientMock.empty ++ JobServiceMock.empty

        val effect = SyncService.sync(account.id)

        val r = ZIO.scoped(effect) `as` assertCompletes

        r.provideSomeLayer[Tracing](mockEnv >>> SyncService.live())
      }
    },
    test("Successful Sync with Lock") {
      check(mailAccountGen, mailTokenGen, timestampGen) { (account, token, timestamp) =>
        val cursor = Cursor(timestamp, token, total = TotalMessages(1), isSyncing = false)
        val mail = Mail(accountKey = account.id, cursor = Some(cursor))
        val mailKeys = NonEmptyList(MailData.Id("mail1"), MailData.Id("mail2"))
        val nextToken = MailToken("nextToken")

        val mockEnv = MailServiceMock.LoadAccount(
          assertion = Assertion.equalTo(account.id),
          result = Expectation.value(Some(account)),
        ) ++
          LockManagerMock.AcquireScoped(
            assertion = Assertion.equalTo(account.lock),
            result = Expectation.value(true),
          ) ++
          MailServiceMock.LoadState(
            assertion = Assertion.equalTo(account.id),
            result = Expectation.value(Some(mail)),
          ) ++
          JobServiceMock.StartJob(
            assertion = isPayload_StartJob(timestamp, JobMeasurement.Variable(), SyncService.MailSync),
            result = Expectation.unit,
          ) ++
          MailServiceMock.StartSync(
            assertion = isPayload_StartSync(account.id, MailLabels.All(), timestamp),
            result = Expectation.unit,
          ) ++
          MailClientMock.FetchMails(
            assertion = Assertion.equalTo((account.id, mail.cursor.map(_.token), Set())),
            result = Expectation.value(Some((mailKeys, nextToken))),
          ) ++
          MailServiceMock.RecordSynced(
            assertion = isPayload_RecordSync(account.id, timestamp, mailKeys, nextToken),
            result = Expectation.unit,
          ) ++
          JobServiceMock.IsCancelled(
            assertion = Assertion.anything,
            result = Expectation.value(false),
          ) ++
          MailClientMock.FetchMails(
            assertion = Assertion.equalTo((account.id, Some(nextToken), Set())),
            result = Expectation.value(None),
          ) ++
          MailServiceMock.CompleteSync(
            assertion = isPayload_CompleteSync(account.id, timestamp),
            result = Expectation.unit,
          ) ++
          JobServiceMock.CompleteJob(
            assertion = isPayload_CompleteJob(timestamp, Job.CompletionReason.Success()),
            result = Expectation.unit,
          )

        val effect = SyncService.sync(account.id)

        val r = ZIO.scoped(effect) `as` assertCompletes

        r.provideSomeLayer[Tracing](mockEnv.toLayer >>> SyncService.live())
      }
    },
    test("Failed Sync with Lock") {
      check(mailAccountGen, mailTokenGen, timestampGen) { (account, token, timestamp) =>
        val cursor = Cursor(timestamp, token, total = TotalMessages(1), isSyncing = false)
        val mail = Mail(accountKey = account.id, cursor = Some(cursor))

        val mockEnv = MailServiceMock.LoadAccount(
          assertion = Assertion.equalTo(account.id),
          result = Expectation.value(Some(account)),
        ) ++
          LockManagerMock.AcquireScoped(
            assertion = Assertion.equalTo(account.lock),
            result = Expectation.value(true),
          ) ++
          MailServiceMock.LoadState(
            assertion = Assertion.equalTo(account.id),
            result = Expectation.value(Some(mail)),
          ) ++
          JobServiceMock.StartJob(
            assertion = isPayload_StartJob(timestamp, JobMeasurement.Variable(), SyncService.MailSync),
            result = Expectation.unit,
          ) ++
          MailServiceMock.StartSync(
            assertion = isPayload_StartSync(account.id, MailLabels.All(), timestamp),
            result = Expectation.unit,
          ) ++
          MailClientMock.FetchMails(
            assertion = Assertion.equalTo((account.id, mail.cursor.map(_.token), Set())),
            result = Expectation.failure(new RuntimeException("FetchMails failed")),
          ) ++
          MailServiceMock.CompleteSync(
            assertion = isPayload_CompleteSync(account.id, timestamp),
            result = Expectation.unit,
          ) ++
          JobServiceMock.CompleteJob(
            assertion = isPayload_CompleteJob(timestamp, Job.CompletionReason.Failure("FetchMails failed")),
            result = Expectation.unit,
          )

        val effect = SyncService.sync(account.id)

        val r = ZIO.scoped(effect) `as` assertCompletes

        r.provideSomeLayer[Tracing](mockEnv.toLayer >>> SyncService.live())
      }
    },
    test("Successful Sync with Lock and Release") {
      check(mailAccountGen, mailTokenGen, timestampGen) { (account, token, timestamp) =>
        val cursor = Cursor(timestamp, token, total = TotalMessages(1), isSyncing = false)
        val mail = Mail(accountKey = account.id, cursor = Some(cursor))
        val mailKeys = NonEmptyList(MailData.Id("mail1"), MailData.Id("mail2"))
        val nextToken = MailToken("nextToken")

        val mockEnv = MailServiceMock.LoadAccount(
          assertion = Assertion.equalTo(account.id),
          result = Expectation.value(Some(account)),
        ) ++
          LockRepositoryMock.Acquire(
            assertion = Assertion.equalTo(account.lock),
            result = Expectation.value(true),
          ) ++
          MailServiceMock.LoadState(
            assertion = Assertion.equalTo(account.id),
            result = Expectation.value(Some(mail)),
          ) ++
          JobServiceMock.StartJob(
            assertion = isPayload_StartJob(timestamp, JobMeasurement.Variable(), SyncService.MailSync),
            result = Expectation.unit,
          ) ++
          MailServiceMock.StartSync(
            assertion = isPayload_StartSync(account.id, MailLabels.All(), timestamp),
            result = Expectation.unit,
          ) ++
          MailClientMock.FetchMails(
            assertion = Assertion.equalTo((account.id, mail.cursor.map(_.token), Set())),
            result = Expectation.value(Some((mailKeys, nextToken))),
          ) ++
          MailServiceMock.RecordSynced(
            assertion = isPayload_RecordSync(account.id, timestamp, mailKeys, nextToken),
            result = Expectation.unit,
          ) ++
          JobServiceMock.IsCancelled(
            assertion = Assertion.anything,
            result = Expectation.value(false),
          ) ++ MailClientMock.FetchMails(
            assertion = Assertion.equalTo((account.id, Some(nextToken), Set())),
            result = Expectation.value(None),
          ) ++
          MailServiceMock.CompleteSync(
            assertion = isPayload_CompleteSync(account.id, timestamp),
            result = Expectation.unit,
          ) ++
          JobServiceMock.CompleteJob(
            assertion = isPayload_CompleteJob(timestamp, Job.CompletionReason.Success()),
            result = Expectation.unit,
          ) ++
          LockRepositoryMock.Release(
            assertion = Assertion.equalTo(account.lock),
            result = Expectation.value(true),
          )

        val effect = SyncService.sync(account.id)

        val r = ZIO.scoped(effect) `as` assertCompletes

        r.provideSomeLayer[Tracing & ZConnectionPool](
          mockEnv.toLayer >>> ZLayer
            .makeSome[Tracing & ZConnectionPool & MailClient & MailService & JobService & LockRepository, SyncService](
              LockManager.live(),
              SyncService.live(),
            ),
        )
      }
    },
    test("Successful Sync should release after sync ends") {
      check(mailAccountGen, mailTokenGen, timestampGen) { (account, token, timestamp) =>
        val cursor = Cursor(timestamp, token, total = TotalMessages(1), isSyncing = false)
        val mail = Mail(accountKey = account.id, cursor = Some(cursor))
        val mailKeys = NonEmptyList(MailData.Id("mail1"), MailData.Id("mail2"))
        val nextToken = MailToken("nextToken")

        val mockEnv = MailServiceMock.LoadAccount(
          assertion = Assertion.equalTo(account.id),
          result = Expectation.value(Some(account)),
        ) ++
          LockRepositoryMock.Acquire(
            assertion = Assertion.equalTo(account.lock),
            result = Expectation.value(true),
          ) ++
          MailServiceMock.LoadState(
            assertion = Assertion.equalTo(account.id),
            result = Expectation.value(Some(mail)),
          ) ++
          JobServiceMock.StartJob(
            assertion = isPayload_StartJob(timestamp, JobMeasurement.Variable(), SyncService.MailSync),
            result = Expectation.unit,
          ) ++
          MailServiceMock.StartSync(
            assertion = isPayload_StartSync(account.id, MailLabels.All(), timestamp),
            result = Expectation.unit,
          ) ++
          MailClientMock.FetchMails(
            assertion = Assertion.equalTo((account.id, mail.cursor.map(_.token), Set())),
            result = Expectation.value(Some((mailKeys, nextToken))),
          ) ++
          MailServiceMock.RecordSynced(
            assertion = isPayload_RecordSync(account.id, timestamp, mailKeys, nextToken),
            result = Expectation.unit,
          ) ++
          JobServiceMock.IsCancelled(
            assertion = Assertion.anything,
            result = Expectation.value(false),
          ) ++ MailClientMock.FetchMails(
            assertion = Assertion.equalTo((account.id, Some(nextToken), Set())),
            result = Expectation.value(None),
          ) ++
          MailServiceMock.CompleteSync(
            assertion = isPayload_CompleteSync(account.id, timestamp),
            result = Expectation.unit,
          ) ++
          JobServiceMock.CompleteJob(
            assertion = isPayload_CompleteJob(timestamp, Job.CompletionReason.Success()),
            result = Expectation.unit,
          ) ++
          LockRepositoryMock.Release(
            assertion = Assertion.equalTo(account.lock),
            result = Expectation.value(true),
          )

        val effect = SyncService.sync(account.id)

        val r = effect `as` assertCompletes

        r.provideSomeLayer[Scope & Tracing & ZConnectionPool](
          mockEnv.toLayer >>> ZLayer
            .makeSome[Tracing & ZConnectionPool & MailClient & MailService & JobService & LockRepository, SyncService](
              LockManager.live(),
              SyncService.live(),
            ),
        )
      }
    },
    test("Successful Sync with Lock and Release when cancelled") {
      check(mailAccountGen, mailTokenGen, timestampGen) { (account, token, timestamp) =>
        val cursor = Cursor(timestamp, token, total = TotalMessages(1), isSyncing = false)
        val mail = Mail(accountKey = account.id, cursor = Some(cursor))
        val mailKeys = NonEmptyList(MailData.Id("mail1"), MailData.Id("mail2"))
        val nextToken = MailToken("nextToken")

        val mockEnv = MailServiceMock.LoadAccount(
          assertion = Assertion.equalTo(account.id),
          result = Expectation.value(Some(account)),
        ) ++
          LockRepositoryMock.Acquire(
            assertion = Assertion.equalTo(account.lock),
            result = Expectation.value(true),
          ) ++
          MailServiceMock.LoadState(
            assertion = Assertion.equalTo(account.id),
            result = Expectation.value(Some(mail)),
          ) ++
          JobServiceMock.StartJob(
            assertion = isPayload_StartJob(timestamp, JobMeasurement.Variable(), SyncService.MailSync),
            result = Expectation.unit,
          ) ++
          MailServiceMock.StartSync(
            assertion = isPayload_StartSync(account.id, MailLabels.All(), timestamp),
            result = Expectation.unit,
          ) ++
          MailClientMock.FetchMails(
            assertion = Assertion.equalTo((account.id, mail.cursor.map(_.token), Set())),
            result = Expectation.value(Some((mailKeys, nextToken))),
          ) ++
          MailServiceMock.RecordSynced(
            assertion = isPayload_RecordSync(account.id, timestamp, mailKeys, nextToken),
            result = Expectation.unit,
          ) ++
          JobServiceMock.IsCancelled(
            assertion = Assertion.anything,
            result = Expectation.value(true),
          ) ++
          MailServiceMock.CompleteSync(
            assertion = isPayload_CompleteSync(account.id, timestamp),
            result = Expectation.unit,
          ) ++
          JobServiceMock.CompleteJob(
            assertion = isPayload_CompleteJob(timestamp, Job.CompletionReason.Cancellation()),
            result = Expectation.unit,
          ) ++
          LockRepositoryMock.Release(
            assertion = Assertion.equalTo(account.lock),
            result = Expectation.value(true),
          )

        val effect = SyncService.sync(account.id)

        val r = ZIO.scoped(effect) `as` assertCompletes

        r.provideSomeLayer[Tracing & ZConnectionPool](
          mockEnv.toLayer >>> ZLayer
            .makeSome[Tracing & ZConnectionPool & MailClient & MailService & JobService & LockRepository, SyncService](
              LockManager.live(),
              SyncService.live(),
            ),
        )
      }
    },
    test("Sync Already in Progress") {
      check(mailAccountGen) { (account) =>
        val mockEnv = MailServiceMock
          .LoadAccount(
            assertion = Assertion.equalTo(account.id),
            result = Expectation.value(Some(account)),
          )
          .toLayer ++
          LockManagerMock
            .AcquireScoped(
              assertion = Assertion.equalTo(account.lock),
              result = Expectation.value(false),
            )
            .toLayer ++
          MailClientMock.empty ++ JobServiceMock.empty

        val effect = SyncService.sync(account.id)

        val r = ZIO.scoped(effect) `as` assertCompletes

        r.provideSomeLayer[Tracing](mockEnv >>> SyncService.live())
      }
    },
  ).provideSomeLayerShared(
    ZLayer.make[ZConnectionPool & Tracing](
      ZConnectionMock.pool(),
      (zio.telemetry.opentelemetry.OpenTelemetry.contextZIO >>> tracingMockLayer()),
    ),
  )

  inline def isPayload_StartJob(timestamp: Timestamp, total: JobMeasurement, tag: Job.Tag) =
    Assertion.assertion[(Job.Id, Timestamp, JobMeasurement, Job.Tag)]("SyncService.isPayload_StartJob") {
      case (_, ts, ttl, tg) =>
        ts == timestamp && ttl == total && tag == tg
    }

  inline def isPayload_StartSync(accountKey: MailAccount.Id, labels: MailLabels, timestamp: Timestamp) =
    Assertion.assertion[(MailAccount.Id, MailLabels, Timestamp, Job.Id)]("SyncService.isPayload_StartSync") {
      case (id, lbls, ts, _) =>
        id == accountKey && lbls == labels && ts == timestamp
    }

  inline def isPayload_RecordSync(
    accountKey: MailAccount.Id,
    timestamp: Timestamp,
    mailKeys: NonEmptyList[MailData.Id],
    nextToken: MailToken,
  ) =
    Assertion.assertion[(MailAccount.Id, Timestamp, NonEmptyList[MailData.Id], MailToken, Job.Id)](
      "SyncService.isPayload_RecordSync",
    ) { case (id, ts, keys, tkn, _) =>
      id == accountKey && ts == timestamp && keys == mailKeys && tkn == nextToken
    }

  inline def isPayload_CompleteSync(accountKey: MailAccount.Id, timestamp: Timestamp) =
    Assertion.assertion[(MailAccount.Id, Timestamp, Job.Id)]("SyncService.isPayload_CompleteSync") { case (id, ts, _) =>
      id == accountKey && ts == timestamp
    }

  inline def isPayload_CompleteJob(timestamp: Timestamp, reason: Job.CompletionReason) =
    Assertion.assertion[(Job.Id, Timestamp, Job.CompletionReason)]("SyncService.isPayload_CompleteJob") {
      case (_, ts, rsn) =>
        ts == timestamp && rsn == reason
    }

}

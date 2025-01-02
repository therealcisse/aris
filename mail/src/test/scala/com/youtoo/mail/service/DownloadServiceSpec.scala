package com.youtoo
package mail
package service

import zio.*
import zio.prelude.*
import zio.jdbc.*
import zio.mock.*
import zio.test.*
import zio.test.Assertion
import zio.test.Assertion.*
import zio.mock.Expectation

import com.youtoo.cqrs.*
import com.youtoo.mail.store.*
import com.youtoo.mail.model.*
import com.youtoo.mail.integration.*
import com.youtoo.lock.*
import com.youtoo.job.service.*
import com.youtoo.job.model.*
import com.youtoo.postgres.*

import zio.telemetry.opentelemetry.tracing.Tracing

object DownloadServiceSpec extends MockSpecDefault, TestSupport {

  def spec = suite("DownloadServiceSpec & DownloadService.DownloadState")(
    suite("DownloadServiceSpec")(
      test("Account Not Found") {
        val mockEnv = MailServiceMock
          .LoadAccount(
            assertion = Assertion.equalTo(MailAccount.Id(Key(1))),
            result = Expectation.value(None),
          )
          .toLayer ++ MailClientMock.empty ++ LockManagerMock.empty ++ JobServiceMock.empty ++ MockDownloadCQRS.empty ++ MockMailEventStore.empty

        val effect = DownloadService.download(MailAccount.Id(Key(1)))

        val r = assertZIO(ZIO.scoped(effect).exit)(failsWithA[IllegalArgumentException])

        r.provideSomeLayer[ZConnectionPool & Tracing](mockEnv >>> DownloadService.live())
      },
      test("Mail State Not Found") {
        check(mailAccountGen) { account =>
          val mockEnv = (MailServiceMock.LoadAccount(
            assertion = Assertion.equalTo(account.id),
            result = Expectation.value(Some(account)),
          ) ++ LockManagerMock.AcquireScoped(
            assertion = Assertion.equalTo(account.lock),
            result = Expectation.value(true),
          ) ++ MailServiceMock.LoadDownloadState(
            assertion = Assertion.equalTo(account.id),
            result = Expectation.value(None),
          )).toLayer ++ MailClientMock.empty ++ JobServiceMock.empty ++ MockDownloadCQRS.empty ++ MockMailEventStore.empty

          val effect = DownloadService.download(account.id)

          val r = ZIO.scoped(effect) `as` assertCompletes

          r.provideSomeLayer[ZConnectionPool & Tracing](mockEnv >>> DownloadService.live())
        }
      },
      test("Not Authorized") {
        check(mailAccountGen, unauthorizedDownloadGen) { (account, download) =>
          val mockEnv = (MailServiceMock.LoadAccount(
            assertion = Assertion.equalTo(account.id),
            result = Expectation.value(Some(account)),
          ) ++ LockManagerMock.AcquireScoped(
            assertion = Assertion.equalTo(account.lock),
            result = Expectation.value(true),
          ) ++ MailServiceMock.LoadDownloadState(
            assertion = Assertion.equalTo(account.id),
            result = Expectation.value(Some(download)),
          )).toLayer ++ MailClientMock.empty ++ JobServiceMock.empty ++ MockDownloadCQRS.empty ++ MockMailEventStore.empty

          val effect = DownloadService.download(account.id)

          val r = ZIO.scoped(effect) `as` assertCompletes

          r.provideSomeLayer[ZConnectionPool & Tracing](mockEnv >>> DownloadService.live())
        }
      },
      // test("Successful Download with Lock") {
      //   check(
      //     mailAccountGen,
      //     versionGen,
      //     Gen.listOf(mailDataGen).map(_.map(m => (m.id, m)).toMap),
      //     timestampGen,
      //     authorizationGrantedGen,
      //     mailTokenGen,
      //     jobIdGen,
      //   ) {
      //     (
      //       account,
      //       lastVersion,
      //       mailsMap,
      //       timestamp,
      //       authorization,
      //       token,
      //       jobId,
      //     ) =>
      //       val download = Download(accout.id, lastVersion, authorization)
      //       val mailKeys = NonEmptyList.fromIterableOption(mailsMap.keys)
      //
      //       val saves = mailsMap.values.iterator.sliding(DownloadService.BatchSize).map { batch =>
      //         val ms = NonEmptyList.fromIterableOption(batch)
      //
      //         MailServiceMock.SaveMails(
      //           assertion = Assertion.equalTo(ms),
      //           result = Expectation.value(ms.size.toLong),
      //         )
      //       }
      //
      //       val mockEnv = MailServiceMock.LoadAccount(
      //         assertion = Assertion.equalTo(account.id),
      //         result = Expectation.value(Some(account)),
      //       ) ++
      //         LockManagerMock.AcquireScoped(
      //           assertion = Assertion.equalTo(account.lock),
      //           result = Expectation.value(true),
      //         ) ++
      //         MailServiceMock.LoadDownlaodState(
      //           assertion = Assertion.equalTo(account.id),
      //           result = Expectation.value(Some(download)),
      //         ) ++
      //         JobServiceMock.StartJob(
      //           assertion = isPayload_StartJob(timestamp, JobMeasurement.Variable(), DownloadService.DownloadSync),
      //           result = Expectation.unit,
      //         ) ++
      //         MockMailEventStore.ReadEvents.FullArgsByAggregate(
      //           equalTo(
      //             (
      //               account.id.asKey,
      //               PersistenceQuery.ns(MailEvent.NS.MailSynced),
      //               lastVersion match {
      //                 case None => FetchOptions().limit(1L).asc()
      //                 case Some(version) => FetchOptions().offset(version.asKey).limit(1L).asc()
      //
      //               },
      //             ),
      //           ),
      //           value {
      //             mailKeys map { keys =>
      //               NonEmptyList(
      //                 Change(version, MailEvent.MailSynced(timestamp, keys, token, jobId)),
      //               )
      //             }
      //           },
      //         ) ++
      //         JobServiceMock.IsCancelled(
      //           assertion = Assertion.anything,
      //           result = Expectation.value(false),
      //         ) ++
      //         (
      //           mailKeys match {
      //             case None => MockMailClient.empty
      //             case Some(keys) =>
      //               val start = MockMailClient.LoadMessage(
      //                 assertion = Assertion.equalTo(keys.head),
      //                 result = Expectation.value(mailsMap.get(keys.head)),
      //               )
      //
      //               keys.tail.foldLeft(start) { case (acc, id) =>
      //                 acc ++ MockMailClient.LoadMessage(
      //                   assertion = Assertion.equalTo(id),
      //                   result = Expectation.value(mailsMap.get(id)),
      //                 )
      //               }
      //           }
      //         ) ++
      //         MockMailEventStore.ReadEvents.FullArgsByAggregate(
      //           equalTo(
      //             (
      //               mailAccount.id.asKey,
      //               PersistenceQuery.ns(MailEvent.NS.MailSynced),
      //               lastVersion match {
      //                 case None => FetchOptions().limit(1L).asc()
      //                 case Some(version) => FetchOptions().offset(version.asKey).limit(1L).asc()
      //
      //               },
      //             ),
      //           ),
      //           value (None)
      //         ) ++
      //       JobServiceMock.CompleteJob(
      //         assertion = isPayload_CompleteJob(timestamp, Job.CompletionReason.Success()),
      //         result = Expectation.unit,
      //       )
      //
      //       val effect = DownloadService.download(account.id)
      //
      //       val r = ZIO.scoped(effect) `as` assertCompletes
      //
      //       r.provideSomeLayer[Tracing](mockEnv.toLayer ++ MockDownloadEventStore.empty >>> DownloadService.live())
      //   }
      // },
      test("Download Already in Progress") {
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
            MailClientMock.empty ++ JobServiceMock.empty ++ MockMailEventStore.empty ++ MockDownloadCQRS.empty

          val effect = DownloadService.download(account.id)

          val r = ZIO.scoped(effect) `as` assertCompletes

          r.provideSomeLayer[ZConnectionPool & Tracing](mockEnv >>> DownloadService.live())
        }
      },
    ).provideSomeLayerShared(
      ZLayer.make[ZConnectionPool & Tracing](
        ZConnectionMock.pool(),
        (zio.telemetry.opentelemetry.OpenTelemetry.contextZIO >>> tracingMockLayer()),
      ),
    ),
    suite("DownloadState")(
      test("next should increment iterations and update token") {
        val initialState = DownloadService.DownloadState(Timestamp(1000L), Some(Version(0L)), 1)
        val nextVersion = Version(1L)
        val nextState = initialState.next(nextVersion)
        assertTrue(nextState.iterations == 2) &&
        assertTrue(nextState.version == Some(nextVersion))
      },
      test("isExpired should return false if neither duration nor iterations exceed limits") {
        val initialState = DownloadService.DownloadState(Timestamp(1000L), Some(Version(0L)), 1)
        val options = DownloadOptions(
          maxDuration = Some(Duration.fromMillis(2000L)),
          maxIterations = Some(3),
          Duration.Infinity,
          DownloadOptions.Retry(None, None),
        )
        val currentTime = Timestamp(2500L)
        assertTrue(!initialState.isExpired(options, currentTime))
      },
      test("isExpired should return true if duration exceeds limit") {
        val initialState = DownloadService.DownloadState(Timestamp(1000L), Some(Version(0L)), 1)
        val options = DownloadOptions(
          maxDuration = Some(Duration.fromMillis(500)),
          maxIterations = Some(3),
          Duration.Infinity,
          DownloadOptions.Retry(None, None),
        )
        val currentTime = Timestamp(2000L)
        assertTrue(initialState.isExpired(options, currentTime))
      },
      test("isExpired should return true if iterations exceed limit") {
        val initialState = DownloadService.DownloadState(Timestamp(1000L), Some(Version(0L)), 3)
        val options = DownloadOptions(
          maxDuration = Some(Duration.fromMillis(2000L)),
          maxIterations = Some(2),
          Duration.Infinity,
          DownloadOptions.Retry(None, None),
        )
        val currentTime = Timestamp(1500L)
        assertTrue(initialState.isExpired(options, currentTime))
      },
      test("isExpired should return false if maxDuration and maxIterations are None") {
        val initialState = DownloadService.DownloadState(Timestamp(1000L), Some(Version(0L)), 1)
        val options =
          DownloadOptions(
            maxDuration = None,
            maxIterations = None,
            Duration.Infinity,
            DownloadOptions.Retry(None, None),
          )
        val currentTime = Timestamp(3000L)
        assertTrue(!initialState.isExpired(options, currentTime))
      },
    ),
  )

  inline def isPayload_StartJob(timestamp: Timestamp, total: JobMeasurement, tag: Job.Tag) =
    Assertion.assertion[(Job.Id, Timestamp, JobMeasurement, Job.Tag)]("DownloadService.isPayload_StartJob") {
      case (_, ts, ttl, tg) =>
        ts == timestamp && ttl == total && tag == tg
    }

  inline def isPayload_StartSync(accountKey: MailAccount.Id, labels: MailLabels, timestamp: Timestamp) =
    Assertion.assertion[(MailAccount.Id, MailLabels, Timestamp, Job.Id)]("DownloadService.isPayload_StartSync") {
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
      "DownloadService.isPayload_RecordSync",
    ) { case (id, ts, keys, tkn, _) =>
      id == accountKey && ts == timestamp && keys == mailKeys && tkn == nextToken
    }

  inline def isPayload_CompleteSync(accountKey: MailAccount.Id, timestamp: Timestamp) =
    Assertion.assertion[(MailAccount.Id, Timestamp, Job.Id)]("DownloadService.isPayload_CompleteSync") {
      case (id, ts, _) =>
        id == accountKey && ts == timestamp
    }

  inline def isPayload_CompleteJob(timestamp: Timestamp, reason: Job.CompletionReason) =
    Assertion.assertion[(Job.Id, Timestamp, Job.CompletionReason)]("DownloadService.isPayload_CompleteJob") {
      case (_, ts, rsn) =>
        ts == timestamp && rsn == reason
    }

}

package com.youtoo
package mail
package service

import zio.prelude.*
import zio.test.*
import zio.mock.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*
import zio.jdbc.*

import com.youtoo.postgres.*
import com.youtoo.cqrs.domain.*

import com.youtoo.cqrs.Codecs.given

import com.youtoo.mail.repository.*

import com.youtoo.mail.model.*
import com.youtoo.mail.store.*
import com.youtoo.cqrs.*
import com.youtoo.mail.integration.*

object MailServiceSpec extends MockSpecDefault, TestSupport {
  inline val Threshold = 10

  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    Log.layer >>> testEnvironment ++ Runtime.setConfigProvider(
      ConfigProvider.fromMap(Map("Mail.snapshots.threshold" -> s"$Threshold")),
    )

  def spec = suite("MailServiceSpec")(
    test("save returns expected result using MailRepository") {
      check(mailAccountGen) { case mailAccount =>
        val expected = 1L

        val mockEnv = MailRepositoryMock.SaveAccount(
          equalTo(mailAccount),
          value(expected),
        )

        (for {
          effect <- MailService.save(mailAccount)
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            MailRepository & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
            MockDownloadEventStore.empty,
            MockAuthorizationEventStore.empty,
            MockAuthorizationCQRS.empty,
            MockMailEventStore.empty,
            MailService.live(),
            MockMailCQRS.empty,
          ),
        )

      }
    },
    test("startSync calls MailCQRS.add") {
      check(mailAccountGen, mailLabelsGen, timestampGen, jobIdGen) { case (account, labels, timestamp, jobId) =>
        val expectedCommand = MailCommand.StartSync(labels, timestamp, jobId)
        val mockEnv = MockMailCQRS.Add(
          equalTo((account.id.asKey, expectedCommand)),
          unit,
        )

        (for {
          effect <- MailService.startSync(account.id, labels, timestamp, jobId)
          testResult = assert(effect)(isUnit)
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            MailCQRS & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
            MockDownloadEventStore.empty,
            MockAuthorizationEventStore.empty,
            MockAuthorizationCQRS.empty,
            MockMailEventStore.empty,
            MailService.live(),
            MailRepositoryMock.empty,
          ),
        )
      }
    },
    test("grantAuthorization calls AuthorizationCQRS.add") {
      check(mailAccountGen, tokenInfoGen, timestampGen) { case (account, tokenInfo, timestamp) =>
        val expectedCommand = AuthorizationCommand.GrantAuthorization(tokenInfo, timestamp)
        val mockEnv = MockAuthorizationCQRS.Add(
          equalTo((account.id.asKey, expectedCommand)),
          unit,
        )

        (for {
          effect <- MailService.grantAuthorization(account.id, tokenInfo, timestamp)
          testResult = assert(effect)(isUnit)
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            AuthorizationCQRS & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
            MockAuthorizationEventStore.empty,
            MockMailCQRS.empty,
            MockDownloadEventStore.empty,
            MockMailEventStore.empty,
            MailService.live(),
            MailRepositoryMock.empty,
          ),
        )
      }
    },
    test("revokeAuthorization calls MockAuthorizationCQRS.add") {
      check(mailAccountGen, timestampGen) { case (account, timestamp) =>
        val expectedCommand = AuthorizationCommand.RevokeAuthorization(timestamp)
        val mockEnv = MockAuthorizationCQRS.Add(
          equalTo((account.id.asKey, expectedCommand)),
          unit,
        )

        (for {
          effect <- MailService.revokeAuthorization(account.id, timestamp)
          testResult = assert(effect)(isUnit)
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            AuthorizationCQRS & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
            MockAuthorizationEventStore.empty,
            MockMailCQRS.empty,
            MockDownloadEventStore.empty,
            MockMailEventStore.empty,
            MailService.live(),
            MailRepositoryMock.empty,
          ),
        )
      }
    },
    test("recordSynced calls MailCQRS.add") {
      check(mailAccountGen, timestampGen, mailDataIdGen, Gen.listOf(mailDataIdGen), mailTokenGen, jobIdGen) {
        case (account, timestamp, key, mailKeys, token, jobId) =>
          val expectedCommand = MailCommand.RecordSync(timestamp, NonEmptyList(key, mailKeys*), token, jobId)
          val mockEnv = MockMailCQRS.Add(
            equalTo((account.id.asKey, expectedCommand)),
            unit,
          )

          (for {
            effect <- MailService.recordSynced(account.id, timestamp, NonEmptyList(key, mailKeys*), token, jobId)
            testResult = assert(effect)(isUnit)
          } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
            mockEnv.toLayer >>> ZLayer.makeSome[
              MailCQRS & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
              MailService,
            ](
              MockAuthorizationEventStore.empty,
              MockAuthorizationCQRS.empty,
              MockDownloadEventStore.empty,
              MockMailEventStore.empty,
              MailService.live(),
              MailRepositoryMock.empty,
            ),
          )
      }
    },
    test("completeSync calls MailCQRS.add") {
      check(mailAccountGen, timestampGen, jobIdGen) { case (account, timestamp, jobId) =>
        val expectedCommand = MailCommand.CompleteSync(timestamp, jobId)
        val mockEnv = MockMailCQRS.Add(
          equalTo((account.id.asKey, expectedCommand)),
          unit,
        )

        (for {
          effect <- MailService.completeSync(account.id, timestamp, jobId)
          testResult = assert(effect)(isUnit)
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            MailCQRS & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
            MockAuthorizationEventStore.empty,
            MockAuthorizationCQRS.empty,
            MockDownloadEventStore.empty,
            MockMailEventStore.empty,
            MailService.live(),
            MailRepositoryMock.empty,
          ),
        )
      }
    },
    test("loadAccount returns expected result using MailRepository") {
      check(mailAccountGen) { case mailAccount =>
        val expected = Some(mailAccount)

        val mockEnv = MailRepositoryMock.LoadAccount(
          equalTo(mailAccount.id),
          value(expected),
        )

        (for {
          effect <- MailService.loadAccount(mailAccount.id)
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            MailRepository & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
            MockAuthorizationEventStore.empty,
            MockAuthorizationCQRS.empty,
            MockDownloadEventStore.empty,
            MockMailEventStore.empty,
            MailService.live(),
            MockMailCQRS.empty,
          ),
        )
      }
    },
    test("loadMail returns expected result using MailRepository") {
      check(mailDataGen) { case mailData =>
        val expected = Some(mailData)

        val mockEnv = MailRepositoryMock.LoadMail(
          equalTo(mailData.id),
          value(expected),
        )

        (for {
          effect <- MailService.loadMail(mailData.id)
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            MailRepository & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
            MockAuthorizationEventStore.empty,
            MockAuthorizationCQRS.empty,
            MockDownloadEventStore.empty,
            MockMailEventStore.empty,
            MailService.live(),
            MockMailCQRS.empty,
          ),
        )
      }
    },
    test("loadState returns expected result using MailEventStore and MailRepository") {
      check(
        mailAccountGen,
        Gen.option(mailCursorGen),
        versionGen,
        jobIdGen,
        Gen.boolean,
        syncCompletedChangeGen,
        authorizationGen,
      ) { case (mailAccount, cursor, version, jobId, hasAccount, completed, authorization) =>
        val expected = Mail(mailAccount.id, cursor, authorization)

        val loadEnv = MailRepositoryMock.LoadAccount(
          equalTo(mailAccount.id),
          value(if hasAccount then Some(mailAccount) else None),
        )

        val mockEnv = MockMailEventStore.ReadEvents.FullArgsByAggregate(
          equalTo(
            (
              mailAccount.id.asKey,
              PersistenceQuery.anyNamespace(
                MailEvent.NS.SyncStarted,
                MailEvent.NS.MailSynced,
              ),
              FetchOptions(),
            ),
          ),
          value {
            cursor match {
              case None => None
              case Some(Cursor(ts, token, totalMessages, isSyncing)) =>
                val keys =
                  NonEmptyList(MailData.Id("1"), (2 to totalMessages.value).map(i => MailData.Id(s"$i")).toList*)

                Some {
                  NonEmptyList(
                    Change(version, MailEvent.MailSynced(ts, keys, token, jobId)),
                    (if isSyncing then Nil else completed :: Nil)*,
                  )
                }
            }
          },
        )

        val mockAuthorizationEnv = MockAuthorizationEventStore.ReadEvents.FullArgsByAggregate(
          equalTo(
            (
              mailAccount.id.asKey,
              PersistenceQuery.anyNamespace(
                AuthorizationEvent.NS.AuthorizationGranted,
                AuthorizationEvent.NS.AuthorizationRevoked,
              ),
              FetchOptions().desc().limit(1L),
            ),
          ),
          value {
            authorization match {
              case Authorization.Granted(token, timestamp) =>
                Some(NonEmptyList(Change(version, AuthorizationEvent.AuthorizationGranted(token, timestamp))))
              case Authorization.Revoked(timestamp) =>
                Some(NonEmptyList(Change(version, AuthorizationEvent.AuthorizationRevoked(timestamp))))
              case _ => None
            }
          },
        )

        val layers =
          if hasAccount then (loadEnv ++ mockEnv ++ mockAuthorizationEnv).toLayer
          else loadEnv.toLayer ++ MockMailEventStore.empty ++ MockAuthorizationEventStore.empty

        (for {
          effect <- MailService.loadState(mailAccount.id)
          expectation = if hasAccount then Some(expected) else None
          testResult = assert(effect)(equalTo(expectation))
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          layers >>> ZLayer.makeSome[
            MailRepository & MailEventStore & AuthorizationEventStore & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
            MockAuthorizationCQRS.empty,
            MockDownloadEventStore.empty,
            MailService.live(),
            MockMailCQRS.empty,
          ),
        )
      }
    },
    test("loadDownloadState returns expected result using DownloadEventStore and MailRepository") {
      check(
        versionGen,
        Gen.option(versionGen),
        versionGen,
        jobIdGen,
        mailAccountGen,
        Gen.boolean,
        authorizationGen,
      ) { case (versionEvent, lastVersion, version, jobId, mailAccount, hasAccount, authorization) =>
        val expected = Download(mailAccount.id, lastVersion, authorization)

        val loadEnv = MailRepositoryMock.LoadAccount(
          equalTo(mailAccount.id),
          value(if hasAccount then Some(mailAccount) else None),
        )

        val mockEnv = MockDownloadEventStore.ReadEvents.FullArgsByAggregate(
          equalTo(
            (
              mailAccount.id.asKey,
              PersistenceQuery.ns(DownloadEvent.NS.Downloaded),
              FetchOptions().desc().limit(1L),
            ),
          ),
          value {
            lastVersion map { v =>
              NonEmptyList(
                Change(versionEvent, DownloadEvent.Downloaded(v, jobId)),
              )
            }
          },
        )

        val mockAuthorizationEnv = MockAuthorizationEventStore.ReadEvents.FullArgsByAggregate(
          equalTo(
            (
              mailAccount.id.asKey,
              PersistenceQuery.anyNamespace(
                AuthorizationEvent.NS.AuthorizationGranted,
                AuthorizationEvent.NS.AuthorizationRevoked,
              ),
              FetchOptions().desc().limit(1L),
            ),
          ),
          value {
            authorization match {
              case Authorization.Granted(token, timestamp) =>
                Some(NonEmptyList(Change(version, AuthorizationEvent.AuthorizationGranted(token, timestamp))))
              case Authorization.Revoked(timestamp) =>
                Some(NonEmptyList(Change(version, AuthorizationEvent.AuthorizationRevoked(timestamp))))
              case _ => None
            }
          },
        )

        val layers =
          if hasAccount then
            (
              loadEnv ++ mockEnv ++ mockAuthorizationEnv
            ).toLayer
          else loadEnv.toLayer ++ MockDownloadEventStore.empty ++ MockAuthorizationEventStore.empty

        (for {
          effect <- MailService.loadDownloadState(mailAccount.id)
          expectation = if hasAccount then Some(expected) else None
          testResult = assert(effect)(equalTo(expectation))
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          layers >>> ZLayer.makeSome[
            MailRepository & DownloadEventStore & AuthorizationEventStore & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
            MockMailEventStore.empty,
            MockAuthorizationCQRS.empty,
            MailService.live(),
            MockMailCQRS.empty,
          ),
        )
      }
    },
    test("updateMailSettings returns expected result using MailRepository") {
      check(mailAccountGen, mailSettingsGen) { case (mailAccount, mailSettings) =>
        val expected = 1L

        val mockEnv = MailRepositoryMock.UpdateMailSettings(
          equalTo((mailAccount.id, mailSettings)),
          value(expected),
        )

        (for {
          effect <- MailService.updateMailSettings(mailAccount.id, mailSettings)
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            MailRepository & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
            MockAuthorizationEventStore.empty,
            MockAuthorizationCQRS.empty,
            MockDownloadEventStore.empty,
            MockMailEventStore.empty,
            MailService.live(),
            MockMailCQRS.empty,
          ),
        )
      }
    },
    test("loadMails returns expected result using MailRepository") {
      check(Gen.option(Gen.long(0, 1000)), Gen.long(1, 1_000_1000), mailDataIdGen) { case (offset, limit, mail) =>
        val expected = Chunk(mail)

        val mockEnv = MailRepositoryMock.LoadMails(
          equalTo((offset, limit)),
          value(expected),
        )

        (for {
          effect <- MailService.loadMails(offset = offset, limit = limit)
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            MailRepository & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
            MockAuthorizationEventStore.empty,
            MockAuthorizationCQRS.empty,
            MockDownloadEventStore.empty,
            MockMailEventStore.empty,
            MailService.live(),
            MockMailCQRS.empty,
          ),
        )
      }
    },
    test("save(MailData) returns expected result using MailRepository") {
      check(mailDataGen) { case mailData =>
        val expected = 1L

        val mockEnv = MailRepositoryMock.SaveMail(
          equalTo(mailData),
          value(expected),
        )

        (for {
          effect <- MailService.save(mailData)
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            MailRepository & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
            MockAuthorizationEventStore.empty,
            MockAuthorizationCQRS.empty,
            MockDownloadEventStore.empty,
            MockMailEventStore.empty,
            MailService.live(),
            MockMailCQRS.empty,
          ),
        )

      }
    },
    test("saveMails(MailData) returns expected result using MailRepository") {
      check(mailDataGen, Gen.listOf(mailDataGen)) { case (mailData, moreMailData) =>
        val mails = NonEmptyList(mailData, moreMailData*)
        val expected = mails.size

        val mockEnv = MailRepositoryMock.SaveMails(
          equalTo(mails),
          value(expected),
        )

        (for {
          effect <- MailService.saveMails(mails)
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            MailRepository & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
            MockAuthorizationEventStore.empty,
            MockAuthorizationCQRS.empty,
            MockDownloadEventStore.empty,
            MockMailEventStore.empty,
            MailService.live(),
            MockMailCQRS.empty,
          ),
        )

      }
    },
    test("load many returns expected result using MailRepository") {
      check(mailAccountGen) { case (account) =>
        val expected = Chunk(account)

        val mockEnv = MailRepositoryMock.LoadAccounts(
          returns = value(expected),
        )

        (for {
          effect <- MailService.loadAccounts()
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            MailRepository & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
            MockAuthorizationEventStore.empty,
            MockAuthorizationCQRS.empty,
            MockDownloadEventStore.empty,
            MockMailEventStore.empty,
            MailService.live(),
            MockMailCQRS.empty,
          ),
        )

      }
    },
  ).provideSomeLayerShared(
    ZConnectionMock.pool() ++ (zio.telemetry.opentelemetry.OpenTelemetry.contextZIO >>> tracingMockLayer()),
  ) @@ TestAspect.withLiveClock @@ TestAspect.samples(10) @@ TestAspect.timeout(60.seconds)

}

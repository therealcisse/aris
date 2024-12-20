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
import com.youtoo.cqrs.service.*
import com.youtoo.cqrs.service.postgres.*

import com.youtoo.cqrs.Codecs.given

import com.youtoo.mail.repository.*

import com.youtoo.mail.model.*
import com.youtoo.mail.store.*
import com.youtoo.cqrs.*

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
            MockMailEventStore.empty,
            MailService.live(),
            MockMailCQRS.empty,
          ),
        )
      }
    },
    test("loadState returns expected result using MailEventStore and MailRepository") {
      check(mailAccountGen, Gen.option(mailCursorGen), versionGen, jobIdGen, Gen.boolean, syncCompletedChangeGen) {
        case (mailAccount, cursor, version, jobId, hasAccount, completed) =>
          val expected = Mail(mailAccount.id, cursor)

          val loadEnv = MailRepositoryMock.LoadAccount(
            equalTo(mailAccount.id),
            value(if hasAccount then Some(mailAccount) else None),
          )

          val mockEnv = MockMailEventStore.ReadEvents.FullArgsByAggregate(
            equalTo(
              (
                mailAccount.id.asKey,
                PersistenceQuery.anyNamespace(Namespace(1), Namespace(2)),
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

          val layers = loadEnv.toLayer ++ (if hasAccount then mockEnv.toLayer else MockMailEventStore.empty)

          (for {
            effect <- MailService.loadState(mailAccount.id)
            expectation = if hasAccount then Some(expected) else None
            testResult = assert(effect)(equalTo(expectation))
          } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
            layers >>> ZLayer.makeSome[
              MailEventStore & MailRepository & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
              MailService,
            ](
              MailService.live(),
              MockMailCQRS.empty,
            ),
          )
      }
    },
    test("loadMails returns expected result using MailRepository") {
      check(Gen.option(keyGen), Gen.long, mailDataIdGen) { case (key, limit, mail) =>
        val expected = Chunk(mail)

        val mockEnv = MailRepositoryMock.LoadMails(
          equalTo(FetchOptions(offset = key, limit = Some(limit))),
          value(expected),
        )

        (for {
          effect <- MailService.loadMails(FetchOptions(offset = key, limit = Some(limit)))
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            MailRepository & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
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
            MockMailEventStore.empty,
            MailService.live(),
            MockMailCQRS.empty,
          ),
        )

      }
    },
    test("load many returns expected result using MailRepository") {
      check(Gen.option(keyGen), Gen.long, mailAccountGen) { case (key, limit, account) =>
        val expected = Chunk(account)

        val mockEnv = MailRepositoryMock.LoadAccounts(
          equalTo(FetchOptions(offset = key, limit = Some(limit))),
          value(expected),
        )

        (for {
          effect <- MailService.loadAccounts(FetchOptions(offset = key, limit = Some(limit)))
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            MailRepository & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
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

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

import com.youtoo.sink.*
import com.youtoo.postgres.*
import com.youtoo.cqrs.domain.*

import com.youtoo.cqrs.Codecs.given

import com.youtoo.mail.repository.*

import com.youtoo.sink.model.*
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
      check(
        mailAccountIdGen,
        mailAccountInformationGen,
      ) { case (id, info) =>
        val expected = 1L

        val mockEnv = MailRepositoryMock.SaveAccount(
          equalTo((id, info)),
          value(expected),
        )

        (for {
          effect <- MailService.save(id, info)
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            MailRepository & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
            MailConfigEventStoreMock.empty,
            MockMailConfigCQRS.empty,
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
            MailConfigEventStoreMock.empty,
            MockMailConfigCQRS.empty,
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
            MailConfigEventStoreMock.empty,
            MockMailConfigCQRS.empty,
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
            MailConfigEventStoreMock.empty,
            MockMailConfigCQRS.empty,
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
              MailConfigEventStoreMock.empty,
              MockMailConfigCQRS.empty,
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
            MailConfigEventStoreMock.empty,
            MockMailConfigCQRS.empty,
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
    test("setAutoSync calls MailConfigCQRS.add") {
      check(mailAccountGen, cronExpressionGen) { case (account, cron) =>
        val expectedCommand = MailConfigCommand.EnableAutoSync(cron)
        val mockEnv = MockMailConfigCQRS.Add(
          equalTo((account.id.asKey, expectedCommand)),
          unit,
        )

        (for {
          effect <- MailService.setAutoSync(account.id, cron)
          testResult = assert(effect)(isUnit)
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            MailConfigCQRS & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
            MailConfigEventStoreMock.empty,
            MockDownloadEventStore.empty,
            MockAuthorizationEventStore.empty,
            MockAuthorizationCQRS.empty,
            MockMailEventStore.empty,
            MailService.live(),
            MockMailCQRS.empty,
            MailRepositoryMock.empty,
          ),
        )
      }
    },
    test("disableAutoSync calls MailConfigCQRS.add") {
      check(mailAccountGen) { case (account) =>
        val expectedCommand = MailConfigCommand.DisableAutoSync()
        val mockEnv = MockMailConfigCQRS.Add(
          equalTo((account.id.asKey, expectedCommand)),
          unit,
        )

        (for {
          effect <- MailService.disableAutoSync(account.id)
          testResult = assert(effect)(isUnit)
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            MailConfigCQRS & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
            MailConfigEventStoreMock.empty,
            MockDownloadEventStore.empty,
            MockAuthorizationEventStore.empty,
            MockAuthorizationCQRS.empty,
            MockMailEventStore.empty,
            MailService.live(),
            MockMailCQRS.empty,
            MailRepositoryMock.empty,
          ),
        )
      }
    },
    test("setAuthConfig calls MailConfigCQRS.add") {
      check(mailAccountGen, authConfigGen) { case (account, authConfig) =>
        val expectedCommand = MailConfigCommand.SetAuthConfig(authConfig)
        val mockEnv = MockMailConfigCQRS.Add(
          equalTo((account.id.asKey, expectedCommand)),
          unit,
        )

        (for {
          effect <- MailService.setAuthConfig(account.id, authConfig)
          testResult = assert(effect)(isUnit)
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            MailConfigCQRS & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
            MailConfigEventStoreMock.empty,
            MockDownloadEventStore.empty,
            MockAuthorizationEventStore.empty,
            MockAuthorizationCQRS.empty,
            MockMailEventStore.empty,
            MailService.live(),
            MockMailCQRS.empty,
            MailRepositoryMock.empty,
          ),
        )
      }
    },
    test("linkSink calls MailConfigCQRS.add") {
      check(mailAccountGen, sinkIdGen) { case (account, sinkId) =>
        val expectedCommand = MailConfigCommand.LinkSink(sinkId)
        val mockEnv = MockMailConfigCQRS.Add(
          equalTo((account.id.asKey, expectedCommand)),
          unit,
        )

        (for {
          effect <- MailService.linkSink(account.id, sinkId)
          testResult = assert(effect)(isUnit)
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            MailConfigCQRS & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
            MailConfigEventStoreMock.empty,
            MockDownloadEventStore.empty,
            MockAuthorizationEventStore.empty,
            MockAuthorizationCQRS.empty,
            MockMailEventStore.empty,
            MailService.live(),
            MockMailCQRS.empty,
            MailRepositoryMock.empty,
          ),
        )
      }
    },
    test("unlinkSink calls MailConfigCQRS.add") {
      check(mailAccountGen, sinkIdGen) { case (account, sinkId) =>
        val expectedCommand = MailConfigCommand.UnlinkSink(sinkId)
        val mockEnv = MockMailConfigCQRS.Add(
          equalTo((account.id.asKey, expectedCommand)),
          unit,
        )

        (for {
          effect <- MailService.unlinkSink(account.id, sinkId)
          testResult = assert(effect)(isUnit)
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            MailConfigCQRS & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
            MailConfigEventStoreMock.empty,
            MockDownloadEventStore.empty,
            MockAuthorizationEventStore.empty,
            MockAuthorizationCQRS.empty,
            MockMailEventStore.empty,
            MailService.live(),
            MockMailCQRS.empty,
            MailRepositoryMock.empty,
          ),
        )
      }
    },
    test("loadAccount returns expected result using MailRepository") {
      check(mailAccountIdGen, mailAccountInformationGen) { case (id, info) =>
        val expected = Some(info.toAccount(id, MailConfig.default))

        val mockEnv = MailRepositoryMock.LoadAccount(
          equalTo(id),
          value(Some(info)),
        )

        val mockMailConfigEnv = MailConfigEventStoreMock.ReadEventsByQueryAggregate(
          equalTo(
            (
              id.asKey,
              PersistenceQuery.anyNamespace(
                MailConfigEvent.NS.AutoSyncEnabled,
                MailConfigEvent.NS.AutoSyncDisabled,
                MailConfigEvent.NS.AuthConfigSet,
                MailConfigEvent.NS.SinkLinked,
                MailConfigEvent.NS.SinkUnlinked,
              ),
              FetchOptions(),
            ),
          ),
          value {
            None
          },
        )

        val layers = (mockEnv ++ mockMailConfigEnv).toLayer

        (for {
          effect <- MailService.loadAccount(id)
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          layers >>> ZLayer.makeSome[
            MailRepository & MailConfigEventStore & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
            MockMailConfigCQRS.empty,
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
            MailConfigEventStoreMock.empty,
            MockMailConfigCQRS.empty,
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
        mailAccountIdGen,
        Gen.option(mailCursorGen),
        versionGen,
        jobIdGen,
        syncCompletedChangeGen,
        authorizationGen,
      ) {
        case (
              id,
              cursor,
              version,
              jobId,
              completed,
              authorization,
            ) =>
          val expected = Mail(id, cursor, authorization)

          val mockEnv = MockMailEventStore.ReadEvents.FullArgsByAggregate(
            equalTo(
              (
                id.asKey,
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
                id.asKey,
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

          val layers = (((mockEnv ++ mockAuthorizationEnv) || (mockAuthorizationEnv ++ mockEnv))).toLayer

          (for {
            effect <- MailService.loadState(id)
            expectation = Some(expected)
            testResult = assert(effect)(equalTo(expectation))
          } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
            layers >>> ZLayer.makeSome[
              MailEventStore & AuthorizationEventStore & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
              MailService,
            ](
              MailRepositoryMock.empty,
              MailConfigEventStoreMock.empty,
              MockMailConfigCQRS.empty,
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
        mailAccountIdGen,
        authorizationGen,
      ) {
        case (
              versionEvent,
              lastVersion,
              version,
              jobId,
              id,
              authorization,
            ) =>
          val expected = Download(id, lastVersion, authorization)

          val mockEnv = MockDownloadEventStore.ReadEvents.FullArgsByAggregate(
            equalTo(
              (
                id.asKey,
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
                id.asKey,
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

          val layers = (
            ((mockEnv ++ mockAuthorizationEnv) || (mockAuthorizationEnv ++ mockEnv))
          ).toLayer

          (for {
            effect <- MailService.loadDownloadState(id)
            expectation = Some(expected)
            testResult = assert(effect)(equalTo(expectation))
          } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
            layers >>> ZLayer.makeSome[
              DownloadEventStore & AuthorizationEventStore & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
              MailService,
            ](
              MailRepositoryMock.empty,
              MailConfigEventStoreMock.empty,
              MockMailConfigCQRS.empty,
              MockMailEventStore.empty,
              MockAuthorizationCQRS.empty,
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
            MailConfigEventStoreMock.empty,
            MockMailConfigCQRS.empty,
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
            MailConfigEventStoreMock.empty,
            MockMailConfigCQRS.empty,
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
            MailConfigEventStoreMock.empty,
            MockMailConfigCQRS.empty,
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
      check(
        Gen.listOf(mailAccountIdGen <*> mailAccountInformationGen),
      ) { case (ls) =>
        val expected = Chunk(ls.map { case (id, info) => info.toAccount(id, MailConfig.default) }*)

        val startEnv = ls match {
          case Nil => MailRepositoryMock.LoadAccounts(returns = value(Chunk.empty))
          case (id, info) :: tl =>
            val env = tl.foldLeft(
              MailRepositoryMock.SaveAccount(equalTo((id, info)), value(1L)),
            ) { case (acc, (id, info)) =>
              acc ++ MailRepositoryMock.SaveAccount(equalTo((id, info)), value(1L))
            }

            env ++ MailRepositoryMock.LoadAccounts(returns = value(Chunk(ls.map(_._1)*)))
        }

        val layers = ls match {
          case Nil => startEnv.toLayer ++ MailConfigEventStoreMock.empty
          case (id, info) :: tl =>
            val env = tl.foldLeft(
              MailRepositoryMock.LoadAccount(equalTo(id), value(Some(info))) ++ MailConfigEventStoreMock
                .ReadEventsByQueryAggregate(
                  equalTo(
                    (
                      id.asKey,
                      PersistenceQuery.anyNamespace(
                        MailConfigEvent.NS.AutoSyncEnabled,
                        MailConfigEvent.NS.AutoSyncDisabled,
                        MailConfigEvent.NS.AuthConfigSet,
                        MailConfigEvent.NS.SinkLinked,
                        MailConfigEvent.NS.SinkUnlinked,
                      ),
                      FetchOptions(),
                    ),
                  ),
                  value(None),
                ),
            ) { case (acc, (id, info)) =>
              acc ++ MailRepositoryMock.LoadAccount(equalTo(id), value(Some(info))) ++ MailConfigEventStoreMock
                .ReadEventsByQueryAggregate(
                  equalTo(
                    (
                      id.asKey,
                      PersistenceQuery.anyNamespace(
                        MailConfigEvent.NS.AutoSyncEnabled,
                        MailConfigEvent.NS.AutoSyncDisabled,
                        MailConfigEvent.NS.AuthConfigSet,
                        MailConfigEvent.NS.SinkLinked,
                        MailConfigEvent.NS.SinkUnlinked,
                      ),
                      FetchOptions(),
                    ),
                  ),
                  value(None),
                )
            }

            (startEnv ++ env).toLayer
        }

        (for {
          _ <- ZIO.foreach(ls) { case (id, info) => MailService.save(id, info) }
          effect <- MailService.loadAccounts()
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          layers >>> ZLayer.makeSome[
            MailRepository & MailConfigEventStore & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
            MockMailConfigCQRS.empty,
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
  ) @@ TestAspect.withLiveClock

}

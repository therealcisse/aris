package com.youtoo
package mail
package service

import zio.test.*
import zio.mock.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*
import zio.jdbc.*

import com.youtoo.postgres.*
import com.youtoo.cqrs.service.*
import com.youtoo.cqrs.service.postgres.*

import com.youtoo.mail.repository.*

import com.youtoo.mail.model.*
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
          effect <- atomically(MailService.save(mailAccount))
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            MailRepository & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
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
          effect <- atomically(MailService.loadAccounts(FetchOptions(offset = key, limit = Some(limit))))
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            MailRepository & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MailService,
          ](
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

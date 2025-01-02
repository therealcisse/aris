package com.youtoo
package mail

import zio.test.*
import zio.test.Assertion.*
import zio.*

import com.youtoo.postgres.*
import zio.telemetry.opentelemetry.tracing.*

object DownloadOptionsSpec extends ZIOSpecDefault, TestSupport {

  def spec = suite("DownloadOptionsSpec")(
    suite("config")(
      test("default") {
        val config = ZIO.config[DownloadOptions]
        assertZIO(config)(equalTo(DownloadOptions(None, None, 30.seconds, DownloadOptions.Retry(None, None))))
      },
      test("values set") {
        val config = ZIO.config[DownloadOptions]
        assertZIO(config)(
          equalTo(DownloadOptions(Some(1), Some(1.second), 30.seconds, DownloadOptions.Retry(Some(2), Some(2.seconds)))),
        )
      }.provide(
        Runtime.setConfigProvider {

          ConfigProvider.fromMap(
            Map(
              "mail_download_max_iterations" -> "1",
              "mail_download_max_duration" -> "1s",
              "mail_download_retry_max_times" -> "2",
              "mail_download_retry_interval" -> "2s",
            ),
          )
        },
      ),
      test("timeout set") {
        val config = ZIO.config[DownloadOptions]
        assertZIO(config.map(_.timeout))(equalTo(15.seconds)).provide(
          Runtime.setConfigProvider {
            ConfigProvider.fromMap(
              Map(
                "mail_download_fetch_timeout" -> "15s",
              ),
            )
          },
        )
      },
    ),
    suite("applyZIO")(
      test("no options") {
        val options = DownloadOptions(None, None, Duration.Infinity, DownloadOptions.Retry(None, None))
        var counter = 0
        val effect = options.applyZIO(ZIO.suspendSucceed { counter += 1; ZIO.fail("error") })
        assertZIO(effect.either)(isLeft(equalTo("error"))) *> assert(counter)(equalTo(1))
      },
      test("times set") {
        val options = DownloadOptions(None, None, Duration.Infinity, DownloadOptions.Retry(Some(2), None))
        var counter = 0
        val effect = options.applyZIO(ZIO.suspendSucceed { counter += 1; ZIO.fail("error") }).exit
        assertZIO(effect)(fails(equalTo("error"))) && assert(counter)(equalTo(3))
      },
      test("times and interval set") {
        val options = DownloadOptions(None, None, Duration.Infinity, DownloadOptions.Retry(Some(2), Some(1.millis)))
        var counter = 0

        for {
          fiber <- options.applyZIO(ZIO.suspendSucceed { counter += 1; ZIO.fail("error") }).fork
          _ <- TestClock.adjust(5.millisecond)

          result <- fiber.join.exit
        } yield assert(result)(fails(equalTo("error"))) && assert(counter)(equalTo(3))

      },
      test("interval set") {
        val options = DownloadOptions(None, None, Duration.Infinity, DownloadOptions.Retry(None, Some(1.millis)))
        var counter = 0
        for {
          fiber <- options.applyZIO(ZIO.suspendSucceed { counter += 1; ZIO.fail("error") }).fork
          _ <- TestClock.adjust(5.millisecond)

          _ <- fiber.interrupt
        } yield assert(counter)(isGreaterThan(1))
      },
    ),
  ).provideSomeLayerShared(
    ZLayer.make[Tracing](
      tracingMockLayer(),
      zio.telemetry.opentelemetry.OpenTelemetry.contextZIO,
    ),
  )

}

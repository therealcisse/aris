package com.youtoo
package mail

import zio.test.*
import zio.test.Assertion.*
import zio.*

object SyncOptionsSpec extends ZIOSpecDefault {

  def spec = suite("SyncOptionsSpec")(
    suite("config")(
      test("default") {
        val config = ZIO.config[SyncOptions]
        assertZIO(config)(equalTo(SyncOptions(None, None, SyncOptions.Retry(None, None))))
      },
      test("values set") {
        val config = ZIO.config[SyncOptions]
        assertZIO(config)(equalTo(SyncOptions(Some(1), Some(1.second), SyncOptions.Retry(Some(2), Some(2.seconds)))))
      }.provide(
        Runtime.setConfigProvider {

          ConfigProvider.fromMap(
            Map(
              "mail-sync-max-iterations" -> "1",
              "mail-sync-max-duration" -> "1s",
              "mail-sync-retry-max-times" -> "2",
              "mail-sync-retry-interval" -> "2s",
            ),
          )
        },
      ),
    ),
    suite("retry")(
      test("no options") {
        val options = SyncOptions(None, None, SyncOptions.Retry(None, None))
        var counter = 0
        val effect = options.retry(ZIO.suspendSucceed { counter += 1; ZIO.fail("error") })
        assertZIO(effect.either)(isLeft(equalTo("error"))) *> assert(counter)(equalTo(1))
      },
      test("times set") {
        val options = SyncOptions(None, None, SyncOptions.Retry(Some(2), None))
        var counter = 0
        val effect = options.retry(ZIO.suspendSucceed { counter += 1; ZIO.fail("error") }).exit
        assertZIO(effect)(fails(equalTo("error"))) && assert(counter)(equalTo(3))
      },
      test("times and interval set") {
        val options = SyncOptions(None, None, SyncOptions.Retry(Some(2), Some(1.millis)))
        var counter = 0

        for {
          fiber <- options.retry(ZIO.suspendSucceed { counter += 1; ZIO.fail("error") }).fork
          _ <- TestClock.adjust(5.millisecond)

          result <- fiber.join.exit
        } yield assert(result)(fails(equalTo("error"))) && assert(counter)(equalTo(3))

      },
      test("interval set") {
        val options = SyncOptions(None, None, SyncOptions.Retry(None, Some(1.millis)))
        var counter = 0
        for {
          fiber <- options.retry(ZIO.suspendSucceed { counter += 1; ZIO.fail("error") }).fork
          _ <- TestClock.adjust(5.millisecond)

          _ <- fiber.interrupt
        } yield assert(counter)(equalTo(6))
      },
    ),
  )

}

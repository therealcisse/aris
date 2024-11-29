package com.youtoo
package std

import zio.*
import zio.test.*
import zio.test.Assertion.*

object HealthcheckSpec extends ZIOSpecDefault {
  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    Log.layer >>> testEnvironment

  def spec = suite("HealthcheckSpec")(
    test("isRunning returns true after start is called") {
      for {
        ref <- Ref.Synchronized.make(Map.empty[Key, (Timestamp, Fiber[Nothing, Any])])
        healthcheck = new Healthcheck.HealthcheckLive(ref)
        id = Key(1L)
        _ <- healthcheck.start(id, Schedule.spaced(1.second))
        isRunning <- healthcheck.isRunning(id)
      } yield assert(isRunning)(isTrue)
    },
    test("isRunning returns false after stop is called") {
      for {
        ref <- Ref.Synchronized.make(Map.empty[Key, (Timestamp, Fiber[Nothing, Any])])
        healthcheck = new Healthcheck.HealthcheckLive(ref)
        id = Key(1L)
        _ <- ZIO.scoped(healthcheck.start(id, Schedule.spaced(1.second)))
        isRunning <- healthcheck.isRunning(id)
      } yield assert(isRunning)(isFalse)
    },
    test("getHeartbeat returns updated timestamps") {
      for {
        ref <- Ref.Synchronized.make(Map.empty[Key, (Timestamp, Fiber[Nothing, Any])])
        healthcheck = new Healthcheck.HealthcheckLive(ref)
        id = Key(1L)
        handles <- ZIO.scoped(for {

          h <- healthcheck.start(id, Schedule.spaced(1.second))
          _ <- TestClock.adjust(500.millis)
          hb1 <- healthcheck.getHeartbeat(id)
          _ <- TestClock.adjust(1.second)
          hb2 <- healthcheck.getHeartbeat(id)
        } yield (hb1, hb2))

        (hb1, hb2) = handles

        _ <- TestClock.adjust(1.second)
        _ <- TestClock.adjust(1.second)
        isRunning <- healthcheck.isRunning(id)
      } yield assert(hb1)(isSome(anything)) && assert(hb2)(
        isSome(hasField("value", _.value, isGreaterThan(hb1.get.value))),
      ) && assert(isRunning)(isFalse)
    },
    test("getHeartbeat returns None if not running") {
      for {
        ref <- Ref.Synchronized.make(Map.empty[Key, (Timestamp, Fiber[Nothing, Any])])
        healthcheck = new Healthcheck.HealthcheckLive(ref)
        id = Key(1L)
        hb <- healthcheck.getHeartbeat(id)
      } yield assert(hb)(isNone)
    },
    test("heartbeat is no longer updated after stop is called") {
      for {
        ref <- Ref.Synchronized.make(Map.empty[Key, (Timestamp, Fiber[Nothing, Any])])
        healthcheck = new Healthcheck.HealthcheckLive(ref)
        id = Key(1L)
        hb1 <- ZIO.scoped {
          for {
            _ <- healthcheck.start(id, Schedule.spaced(1.second))
            _ <- TestClock.adjust(1.second)
            hb1 <- healthcheck.getHeartbeat(id)

          } yield hb1
        }
        _ <- TestClock.adjust(1.second)
        hb2 <- healthcheck.getHeartbeat(id)
        isRunning <- healthcheck.isRunning(id)
      } yield assert(hb1)(isSome(anything)) && assert(hb2)(isNone) && assert(isRunning)(isFalse)
    },
  ).provideSomeLayerShared(
    zio.telemetry.opentelemetry.OpenTelemetry.contextZIO >>> tracingMockLayer(),
  ) @@ TestAspect.sequential

}

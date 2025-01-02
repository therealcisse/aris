package com.youtoo
package lock

import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.test.ZIOSpecDefault
import zio.mock.Expectation
import zio.mock.Expectation.*
import zio.jdbc.*

import com.youtoo.postgres.*
import com.youtoo.lock.*
import com.youtoo.lock.repository.*
import zio.telemetry.opentelemetry.tracing.*

object LockManagerSpec extends ZIOSpecDefault, TestSupport {
  val lockGen: Gen[Any, Lock] = Gen.uuid.map(i => Lock(i.toString))
  val timestampGen: Gen[Any, Timestamp] = Gen.fromZIO(Timestamp.gen)

  def spec = suite("LockManagerSpec")(
    test("acquireScoped should acquire and release lock successfully") {
      check(lockGen) { lock =>

        val acquireExpectation = LockRepositoryMock.Acquire(equalTo(lock), result = value(true))
        val releaseExpectation = LockRepositoryMock.Release(equalTo(lock), result = value(true))

        val env = ZLayer.make[LockManager & Tracing]( // Define the layer using make
          LockManager.live(),
          acquireExpectation ++ releaseExpectation,
          ZConnectionMock.pool(),
          tracingMockLayer(),
          zio.telemetry.opentelemetry.OpenTelemetry.contextZIO,
        )

        val effect = ZIO.scoped {
          ZIO.serviceWithZIO[LockManager](_.acquireScoped(lock))
        }

        assertZIO(effect.provideLayer(env))(isTrue)
      }
    },
    test("locks should return current locks") {
      check(lockGen, timestampGen) { (lock, timestamp) =>

        val locksExpectation = LockRepositoryMock.Locks(returns = value(Chunk(Lock.Info(lock, timestamp))))

        val env = ZLayer.make[LockManager](
          LockManager.live(),
          locksExpectation,
          ZConnectionMock.pool(),
          tracingMockLayer(),
          zio.telemetry.opentelemetry.OpenTelemetry.contextZIO,
        )

        val effect = ZIO.serviceWithZIO[LockManager](_.locks)

        assertZIO(effect.provideLayer(env))(equalTo(Chunk(Lock.Info(lock, timestamp))))
      }
    },
  )
}

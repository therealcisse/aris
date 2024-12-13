package com.youtoo
package lock
package repository

import zio.*
import zio.jdbc.*
import zio.test.*
import zio.test.Assertion.*

import com.youtoo.postgres.config.*
import com.youtoo.postgres.*

import zio.telemetry.opentelemetry.tracing.*

object LockRepositorySpec extends PgSpec, TestSupport {
  val lockGen: Gen[Any, Lock] = Gen.uuid.map(i => Lock(i.toString))

  def spec = suite("LockRepositorySpec")(
    acquireLockTest,
    releaseLockTest,
    listLocksTest,
    duplicateLockAcquisitionTest,
  ).provideSomeLayerShared(
    ZLayer.make[Tracing & LockRepository](
      tracingMockLayer(),
      zio.telemetry.opentelemetry.OpenTelemetry.contextZIO,
      LockRepository.postgres(),
    ),
  ) @@ TestAspect.withLiveClock @@ TestAspect.nondeterministic @@ TestAspect.beforeAll {
    for {
      config <- ZIO.service[DatabaseConfig]
      _ <- FlywayMigration.run(config)

    } yield ()

  }

  // Test for acquiring a lock
  val acquireLockTest = test("acquire lock should succeed within a scope") {
    check(lockGen) { lock =>
      for {
        repository <- ZIO.service[LockRepository]

        result <- repository.acquire(lock).atomically
      } yield assert(result)(isTrue)

    }

  }

  // Test for releasing a lock
  val releaseLockTest = test("release lock should succeed after acquire") {
    check(lockGen) { lock =>
      atomically {

        for {
          repository <- ZIO.service[LockRepository]
          acquireResult <- repository.acquire(lock)
          releaseResult <- repository.release(lock)
        } yield assert(acquireResult)(isTrue) && assert(releaseResult)(isTrue)

      }

    }
  }

  // Test for listing locks, ensure acquired lock is part of it
  val listLocksTest = test("list locks should include acquired lock") {
    check(lockGen) { lock =>
      for {
        repository <- ZIO.service[LockRepository]

        a <- repository
          .acquire(lock)
          .flatMap { acquired =>
            for {
              locks <- repository.locks
            } yield assert(locks)(contains(lock))
          }
          .atomically

      } yield a
    }

  }

  val duplicateLockAcquisitionTest = test("duplicate lock acquisition should fail") {
    check(lockGen) { lock =>
      atomically {

        for {
          repository <- ZIO.service[LockRepository]
          firstAcquire <- repository.acquire(lock)
          secondAcquire <- repository.acquire(lock)
        } yield assert(firstAcquire)(isTrue) && assert(secondAcquire)(isFalse)
      }

    }
  }

}

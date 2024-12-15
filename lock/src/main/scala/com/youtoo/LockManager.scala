package com.youtoo

import zio.*

import zio.jdbc.*

import com.youtoo.postgres.*
import com.youtoo.lock.repository.*

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*

trait LockManager {
  def aquireScoped(lock: Lock): ZIO[Scope & Tracing, Throwable, Boolean]
  def locks: Task[Chunk[Lock]]

}

object LockManager {

  def live(): ZLayer[LockRepository & ZConnectionPool & Tracing, Throwable, LockManager] =
    ZLayer.fromFunction { (repository: LockRepository, pool: ZConnectionPool, tracing: Tracing) =>
      new LockManagerLive(repository, pool).traced(tracing)
    }

  class LockManagerLive(repository: LockRepository, pool: ZConnectionPool) extends LockManager { self =>

    def aquireScoped(lock: Lock): ZIO[Scope & Tracing, Throwable, Boolean] =
      atomically {

        for {
          _ <- Log.info(s"Acquire lock: $lock")

          a <- repository.acquire(lock).tapError { e =>
            Log.error(s"Failed to aquire lock: $lock", e)
          }

          _ <- ZIO.addFinalizer {
            for {
              a <- repository.release(lock).catchAll(e => Log.error(s"Failed to release lock: $lock", e) `as` false)
              _ <- Log.info(s"Lock $lock released: $a")

            } yield ()

          }

        } yield a

      }.provideSomeEnvironment[Scope & Tracing](_.add[ZConnectionPool](pool))

    def locks: Task[Chunk[Lock]] =
      atomically {
        repository.locks

      }.provideEnvironment(ZEnvironment(pool))

    def traced(tracing: Tracing): LockManager = new LockManager {
      def aquireScoped(lock: Lock): ZIO[Scope & Tracing, Throwable, Boolean] =
        self.aquireScoped(lock) @@ tracing.aspects.span(
          "LockManager.aquireScoped",
          attributes = Attributes(Attribute.string("lock", lock.value)),
        )

      def locks: Task[Chunk[Lock]] = self.locks @@ tracing.aspects.span(
        "LockManager.locks",
      )

    }

  }

}

package com.youtoo
package lock
package repository

import zio.*
import zio.jdbc.*

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*

trait LockRepository {
  def acquire(lock: Lock): RIO[ZConnection, Boolean]
  def release(lock: Lock): RIO[ZConnection, Boolean]
  def locks: RIO[ZConnection, Chunk[Lock.Info]]

}

object LockRepository {
  def postgres(): ZLayer[Tracing, Throwable, LockRepository] =
    ZLayer.fromFunction(new PostgresLockRepository().traced(_))

  class PostgresLockRepository() extends LockRepository { self =>
    def acquire(lock: Lock): RIO[ZConnection, Boolean] =
      for {
        timestamp <- Timestamp.gen

        a <- Queries.ACQUIRE_LOCK(lock, timestamp).selectOne.map(_.getOrElse(false))
      } yield a

    def release(lock: Lock): RIO[ZConnection, Boolean] =
      Queries.RELEASE_LOCK(lock).selectOne.map(_.getOrElse(false))

    def locks: RIO[ZConnection, Chunk[Lock.Info]] =
      Queries.READ_LOCKS.selectAll

    def traced(tracing: Tracing): LockRepository = new LockRepository {
      def acquire(lock: Lock): RIO[ZConnection, Boolean] = self.acquire(lock) @@ tracing.aspects.span(
        "PostgresLockRepository.acquire",
        attributes = Attributes(Attribute.string("lock", lock.value)),
      )

      def release(lock: Lock): RIO[ZConnection, Boolean] = self.release(lock) @@ tracing.aspects.span(
        "PostgresLockRepository.release",
        attributes = Attributes(Attribute.string("lock", lock.value)),
      )

      def locks: RIO[ZConnection, Chunk[Lock.Info]] = self.locks @@ tracing.aspects.span(
        "PostgresLockRepository.locks",
      )

    }

  }

  def memory(): ZLayer[Tracing, Throwable, LockRepository] =
    ZLayer {
      for {
        guard <- Semaphore.make(1)
        map <- Ref.make(Map[Lock, Timestamp]())
        tracing <- ZIO.service[Tracing]

      } yield new MemoryLockRepository(guard, map).traced(tracing)
    }

  class MemoryLockRepository(guard: Semaphore, map: Ref[Map[Lock, Timestamp]]) extends LockRepository { self =>
    def acquire(lock: Lock): RIO[ZConnection, Boolean] =
      guard.withPermit {
        for {
          timestamp <- Timestamp.gen

          a <- map.modify { locks =>
            if locks.contains(lock) then
              (
                false,
                locks,
              )
            else
              (
                true,
                locks + (lock -> timestamp),
              )
          }

        } yield a

      }

    def release(lock: Lock): RIO[ZConnection, Boolean] =
      guard.withPermit {

        map.modify { locks =>
          if locks.contains(lock) then
            (
              true,
              locks - lock,
            )
          else
            (
              false,
              locks,
            )
        }

      }

    def locks: RIO[ZConnection, Chunk[Lock.Info]] =
      for {
        locks <- map.get

      } yield Chunk(locks.map(Lock.Info.apply).toSeq*)

    def traced(tracing: Tracing): LockRepository = new LockRepository {
      def acquire(lock: Lock): RIO[ZConnection, Boolean] = self.acquire(lock) @@ tracing.aspects.span(
        "MemoryLockRepository.acquire",
        attributes = Attributes(Attribute.string("lock", lock.value)),
      )

      def release(lock: Lock): RIO[ZConnection, Boolean] = self.release(lock) @@ tracing.aspects.span(
        "MemoryLockRepository.release",
        attributes = Attributes(Attribute.string("lock", lock.value)),
      )

      def locks: RIO[ZConnection, Chunk[Lock.Info]] = self.locks @@ tracing.aspects.span(
        "MemoryLockRepository.locks",
      )

    }

  }

  object Queries {
    given SqlFragment.Setter[Lock] = SqlFragment.Setter[String].contramap(_.value)
    given SqlFragment.Setter[Timestamp] = SqlFragment.Setter[Long].contramap(_.value)

    given JdbcDecoder[Lock] = JdbcDecoder[String].map(Lock.apply)
    given JdbcDecoder[Timestamp] = JdbcDecoder[Long].map(Timestamp.apply)

    def READ_LOCKS: Query[Lock.Info] =
      sql"""
      SELECT
        lock_key, timestamp
      FROM locks
      """.query[(Lock, Timestamp)].map(Lock.Info.apply)

    def ACQUIRE_LOCK(lock: Lock, timestamp: Timestamp): Query[Boolean] =
      sql"SELECT acquire_lock($lock, $timestamp)".query[Boolean]

    def RELEASE_LOCK(lock: Lock): Query[Boolean] =
      sql"SELECT release_lock($lock)".query[Boolean]

  }

}

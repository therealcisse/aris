package com.youtoo
package lock
package repository

import zio.*
import zio.jdbc.*

import zio.telemetry.opentelemetry.tracing.Tracing

trait LockRepository {
  def acquire(lock: Lock): RIO[ZConnection, Boolean]
  def release(lock: Lock): RIO[ZConnection, Boolean]
  def locks: RIO[ZConnection, Chunk[Lock]]

}

object LockRepository {
  def postgres(): ZLayer[Tracing, Throwable, LockRepository] =
    ZLayer.fromFunction(new LockRepositoryLive().traced(_))

  class LockRepositoryLive() extends LockRepository { self =>
    def acquire(lock: Lock): RIO[ZConnection, Boolean] =
      for {
        timestamp <- Timestamp.now

        a <- Queries.ACQUIRE_LOCK(lock, timestamp).selectOne.map(_.getOrElse(false))
      } yield a

    def release(lock: Lock): RIO[ZConnection, Boolean] =
      Queries.RELEASE_LOCK(lock).selectOne.map(_.getOrElse(false))

    def locks: RIO[ZConnection, Chunk[Lock]] =
      Queries.READ_LOCKS.selectAll

    def traced(tracing: Tracing): LockRepository = new LockRepository {
      def acquire(lock: Lock): RIO[ZConnection, Boolean] = self.acquire(lock) @@ tracing.aspects.span(
        "LockRepository.acquire",
      )

      def release(lock: Lock): RIO[ZConnection, Boolean] = self.release(lock) @@ tracing.aspects.span(
        "LockRepository.release",
      )

      def locks: RIO[ZConnection, Chunk[Lock]] = self.locks @@ tracing.aspects.span(
        "LockRepository.locks",
      )

    }

  }

  object Queries {
    given SqlFragment.Setter[Lock] = SqlFragment.Setter[String].contramap(_.value)
    given SqlFragment.Setter[Timestamp] = SqlFragment.Setter[Long].contramap(_.value)

    given JdbcDecoder[Lock] = JdbcDecoder[String].map(Lock.apply)

    def READ_LOCKS: Query[Lock] =
      sql"""
      SELECT
        lock_key
      FROM locks
      """.query[Lock]

    def ACQUIRE_LOCK(lock: Lock, timestamp: Timestamp): Query[Boolean] =
      sql"SELECT acquire_lock($lock, $timestamp)".query[Boolean]

    def RELEASE_LOCK(lock: Lock): Query[Boolean] =
      sql"SELECT release_lock($lock)".query[Boolean]

  }

}

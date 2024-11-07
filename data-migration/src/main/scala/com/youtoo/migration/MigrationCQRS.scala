package com.youtoo
package migration

import zio.*

import zio.jdbc.*
import zio.prelude.*

import com.youtoo.cqrs.Codecs.*

import com.youtoo.cqrs.domain.*
import com.youtoo.migration.model.*
import com.youtoo.cqrs.*
import com.youtoo.cqrs.service.*
import com.youtoo.migration.store.*

import zio.metrics.*

import java.time.temporal.ChronoUnit

trait MigrationCQRS extends CQRS[MigrationEvent, MigrationCommand] {}

object MigrationCQRS {

  object metrics {
    val addition = Metric.timer(
      "Migration_addition_duration",
      chronoUnit = ChronoUnit.MILLIS,
      boundaries = Chunk.iterate(1.0, 10)(_ + 1.0),
    )

  }

  inline def add(id: Key, cmd: MigrationCommand): RIO[MigrationCQRS, Unit] =
    ZIO.serviceWithZIO[MigrationCQRS](_.add(id, cmd)) @@ metrics.addition.trackDuration

  def live(): ZLayer[
    ZConnectionPool & MigrationEventStore,
    Throwable,
    MigrationCQRS,
  ] =
    ZLayer.fromFunction {
      (
        pool: ZConnectionPool,
        eventStore: MigrationEventStore,
      ) =>
        new LiveMigrationCQRS(
          pool,
          eventStore,
        )

    }

  class LiveMigrationCQRS(
    pool: ZConnectionPool,
    eventStore: MigrationEventStore,
  ) extends MigrationCQRS {

    def add(id: Key, cmd: MigrationCommand): Task[Unit] =
      atomically {
        val evnts = CmdHandler.applyCmd(cmd)

        ZIO.foreachDiscard(evnts) { payload =>
          for {
            version <- Version.gen
            ch = Change(version = version, payload = payload)
            _ <- eventStore.save(id = id, ch)
          } yield ()
        }
      }.provideEnvironment(ZEnvironment(pool))

  }

}

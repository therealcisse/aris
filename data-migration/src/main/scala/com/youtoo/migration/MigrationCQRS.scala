package com.youtoo
package migration

import zio.*

import cats.implicits.*

import zio.jdbc.*
import zio.prelude.*

import com.youtoo.cqrs.Codecs.*

import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.domain.*
import com.youtoo.migration.model.*
import com.youtoo.cqrs.*
import com.youtoo.cqrs.service.*
import com.youtoo.migration.service.*
import com.youtoo.migration.store.*

import zio.metrics.*

import java.time.temporal.ChronoUnit

trait MigrationCQRS extends CQRS[Migration, MigrationEvent, MigrationCommand] {}

object MigrationCQRS {

  object metrics {
    val addition = Metric.timer(
      "Migration_addition_duration",
      chronoUnit = ChronoUnit.MILLIS,
      boundaries = Chunk.iterate(1.0, 10)(_ + 1.0),
    )

  }

  inline def add(id: Key, cmd: MigrationCommand)(using
    Cmd: MigrationCommandHandler,
  ): RIO[MigrationCQRS, Unit] = ZIO.serviceWithZIO[MigrationCQRS](_.add(id, cmd)) @@ metrics.addition.trackDuration

  def live(): ZLayer[
    ZConnectionPool & MigrationEventStore & SnapshotStore &
      SnapshotStrategy.Factory,
    Throwable,
    MigrationCQRS,
  ] =
    ZLayer.fromFunction {
      (
        factory: SnapshotStrategy.Factory,
        eventStore: MigrationEventStore,
        snapshotStore: SnapshotStore,
        pool: ZConnectionPool,
      ) =>
        ZLayer {

          for {
            strategy <- factory.create(MigrationEvent.discriminator)

          } yield new MigrationCQRS {
            private val Cmd = summon[CmdHandler[MigrationCommand, MigrationEvent]]

            def add(id: Key, cmd: MigrationCommand): Task[Unit] =
              atomically {
                val evnts = Cmd.applyCmd(cmd)

                ZIO.foreachDiscard(evnts) { e =>
                  for {
                    version <- Version.gen
                    ch = Change(version = version, payload = e)
                    _ <- eventStore.save(id = id, ch)
                  } yield ()
                }
              }.provideEnvironment(ZEnvironment(pool))

          }

        }

    }.flatten

}

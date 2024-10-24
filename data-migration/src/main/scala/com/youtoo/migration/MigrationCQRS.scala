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

    val load = Metric.timer(
      "Migration_load_duration",
      chronoUnit = ChronoUnit.MILLIS,
      boundaries = Chunk.iterate(1.0, 10)(_ + 1.0),
    )

  }

  inline def add(id: Key, cmd: MigrationCommand)(using
    Cmd: MigrationCommandHandler,
  ): RIO[MigrationCQRS, Unit] = ZIO.serviceWithZIO[MigrationCQRS](_.add(id, cmd)) @@ metrics.addition.trackDuration

  inline def load(id: Key)(using
    Evt: MigrationEventHandler,
  ): RIO[MigrationCQRS, Option[Migration]] =
    ZIO.serviceWithZIO[MigrationCQRS](_.load(id)) @@ metrics.load.trackDuration

  def live(): ZLayer[
    ZConnectionPool & MigrationCheckpointer & MigrationProvider & MigrationEventStore & SnapshotStore &
      SnapshotStrategy.Factory,
    Throwable,
    MigrationCQRS,
  ] =
    ZLayer.fromFunction {
      (
        factory: SnapshotStrategy.Factory,
        checkpointer: MigrationCheckpointer,
        provider: MigrationProvider,
        eventStore: MigrationEventStore,
        snapshotStore: SnapshotStore,
        pool: ZConnectionPool,
      ) =>
        ZLayer {

          for {
            strategy <- factory.create(MigrationEvent.discriminator)

          } yield new MigrationCQRS {
            private val Cmd = summon[CmdHandler[MigrationCommand, MigrationEvent]]
            private val Evt = summon[EventHandler[MigrationEvent, Migration]]

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

            def load(id: Key): Task[Option[Migration]] =
              atomically {
                val deps = (
                  provider.load(id) <&> snapshotStore.readSnapshot(id)
                ).map(_.tupled)

                val o = deps flatMap {
                  case None =>
                    for {
                      events <- eventStore.readEvents(id)
                      inn = events map { es =>
                        (
                          Evt.applyEvents(es),
                          es.maxBy(_.version),
                          es.size,
                          None,
                        )
                      }

                    } yield inn

                  case Some((in, version)) =>
                    val events = eventStore.readEvents(id, snapshotVersion = version)

                    events map (_.map { es =>
                      (
                        Evt.applyEvents(in, es),
                        es.maxBy(_.version),
                        es.size,
                        version.some,
                      )
                    })

                }

                o flatMap (_.fold(ZIO.none) {
                  case (inn, ch, size, version) if strategy(version, size) =>
                    (checkpointer.save(inn) <&> snapshotStore.save(id = id, version = ch.version)) as inn.some

                  case (inn, _, _, _) => ZIO.some(inn)
                })
              }.provideEnvironment(ZEnvironment(pool))

          }
        }

    }.flatten

}

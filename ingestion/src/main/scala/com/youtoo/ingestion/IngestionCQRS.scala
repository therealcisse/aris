package com.youtoo
package ingestion

import zio.*

import cats.implicits.*

import zio.jdbc.*
import zio.prelude.*

import com.youtoo.cqrs.Codecs.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.domain.*
import com.youtoo.ingestion.model.*
import com.youtoo.cqrs.service.*
import com.youtoo.ingestion.service.*
import com.youtoo.ingestion.store.*

import zio.metrics.*

import java.time.temporal.ChronoUnit

trait IngestionCQRS extends CQRS[Ingestion, IngestionEvent, IngestionCommand] {}

object IngestionCQRS {

  object metrics {
    val addition = Metric.timer(
      "Ingestion_addition_duration",
      chronoUnit = ChronoUnit.MILLIS,
      boundaries = Chunk.iterate(1.0, 10)(_ + 1.0),
    )

  }

  inline def add(id: Key, cmd: IngestionCommand)(using
    Cmd: IngestionCommandHandler,
  ): RIO[IngestionCQRS, Unit] = ZIO.serviceWithZIO[IngestionCQRS](_.add(id, cmd)) @@ metrics.addition.trackDuration

  def live(): ZLayer[
    ZConnectionPool & IngestionEventStore & SnapshotStore &
      SnapshotStrategy.Factory,
    Throwable,
    IngestionCQRS,
  ] =
    ZLayer.fromFunction {
      (
        factory: SnapshotStrategy.Factory,
        eventStore: IngestionEventStore,
        snapshotStore: SnapshotStore,
        pool: ZConnectionPool,
      ) =>
        ZLayer {

          for {
            strategy <- factory.create(IngestionEvent.discriminator)

          } yield new IngestionCQRS {
            private val Cmd = summon[CmdHandler[IngestionCommand, IngestionEvent]]

            def add(id: Key, cmd: IngestionCommand): Task[Unit] =
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

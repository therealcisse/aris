package com.youtoo
package ingestion

import zio.*

import zio.jdbc.*
import zio.prelude.*

import com.youtoo.cqrs.Codecs.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.ingestion.model.*
import com.youtoo.cqrs.service.*
import com.youtoo.ingestion.store.*

import zio.metrics.*

import java.time.temporal.ChronoUnit

trait IngestionCQRS extends CQRS[IngestionEvent, IngestionCommand] {}

object IngestionCQRS {

  object metrics {
    val addition = Metric.timer(
      "Ingestion_addition_duration",
      chronoUnit = ChronoUnit.MILLIS,
      boundaries = Chunk.iterate(1.0, 10)(_ + 1.0),
    )

  }

  inline def add(id: Key, cmd: IngestionCommand): RIO[IngestionCQRS, Unit] =
    ZIO.serviceWithZIO[IngestionCQRS](_.add(id, cmd)) @@ metrics.addition.trackDuration

  def live(): ZLayer[
    ZConnectionPool & IngestionEventStore,
    Throwable,
    IngestionCQRS,
  ] =
    ZLayer.fromFunction {
      LiveIngestionCQRS.apply
    }

  class LiveIngestionCQRS(
    pool: ZConnectionPool,
    eventStore: IngestionEventStore,
  ) extends IngestionCQRS {

    def add(id: Key, cmd: IngestionCommand): Task[Unit] =
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

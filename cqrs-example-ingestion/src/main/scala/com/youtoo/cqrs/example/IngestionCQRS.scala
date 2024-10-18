package com.youtoo.cqrs
package example

import zio.*

import cats.implicits.*

import zio.jdbc.*
import zio.prelude.*

import com.youtoo.cqrs.Codecs.*

import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.example.model.*
import com.youtoo.cqrs.service.*
import com.youtoo.cqrs.example.service.*
import com.youtoo.cqrs.example.store.*
import zio.metrics.Metric
import java.time.temporal.ChronoUnit

trait IngestionCQRS extends CQRS[Ingestion, IngestionEvent, IngestionCommand] {}

object IngestionCQRS {
  inline val Threshold = 10

  val countAdds = Metric.counter("Ingestion.adds.counts").fromConst(1)
  val countLoads = Metric.counter("Ingestion.loads.counts").fromConst(1)

  val timeAdds = Metric.timer("Ingestion.adds.duration", ChronoUnit.MILLIS)
  val timeLoads = Metric.timer("Ingestion.load.duration", ChronoUnit.MILLIS)

  inline def add(id: Key, cmd: IngestionCommand)(using
    Cmd: IngestionCommandHandler,
  ): RIO[IngestionCQRS, Unit] = ZIO.serviceWithZIO[IngestionCQRS](_.add(id, cmd)) @@ countAdds @@ timeAdds.trackDuration

  inline def load(id: Key)(using
    Evt: IngestionEventHandler,
  ): RIO[IngestionCQRS, Option[Ingestion]] =
    ZIO.serviceWithZIO[IngestionCQRS](_.load(id)) @@ countLoads @@ timeLoads.trackDuration

  def live(): ZLayer[
    ZConnectionPool & IngestionCheckpointer & IngestionProvider & IngestionEventStore & SnapshotStore,
    Throwable,
    IngestionCQRS,
  ] =
    ZLayer.fromFunction {
      (
        checkpointer: IngestionCheckpointer,
        provider: IngestionProvider,
        eventStore: IngestionEventStore,
        snapshotStore: SnapshotStore,
        pool: ZConnectionPool,
      ) =>
        new IngestionCQRS {
          private val Cmd = summon[CmdHandler[IngestionCommand, IngestionEvent]]
          private val Evt = summon[EventHandler[IngestionEvent, Ingestion]]

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

          def load(id: Key): Task[Option[Ingestion]] =
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
                    )
                  })

              }

              o flatMap (_.fold(ZIO.none) {
                case (inn, ch, size) if size >= Threshold =>
                  for {
                    _ <- checkpointer.save(inn)
                    _ <- snapshotStore.save(id = id, version = ch.version)

                  } yield inn.some
                case (inn, _, _) => ZIO.some(inn)
              })
            }.provideEnvironment(ZEnvironment(pool))

        }

    }

}

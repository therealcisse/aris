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

trait IngestionCQRS extends CQRS[IngestionCommand, IngestionEvent, Ingestion] {}

object IngestionCQRS {
  inline val Threshold = 10

  inline def add(id: Key, cmd: IngestionCommand)(using
    Cmd: IngestionCommandHandler,
  ): RIO[IngestionCQRS & ZConnectionPool & CQRSPersistence, Unit] = ZIO.serviceWithZIO[IngestionCQRS](_.add(id, cmd))

  inline def load(id: Key)(using
    Evt: IngestionEventHandler,
  ): RIO[IngestionCQRS & ZConnectionPool & CQRSPersistence, Option[Ingestion]] =
    ZIO.serviceWithZIO[IngestionCQRS](_.load(id))

  def live(): ZLayer[
    ZConnectionPool & CQRSPersistence & IngestionCheckpointer & IngestionProvider & IngestionEventStore & SnapshotStore,
    Throwable,
    IngestionCQRS,
  ] =
    ZLayer.fromFunction {
      (
        checkpointer: IngestionCheckpointer,
        provider: IngestionProvider,
        eventStore: IngestionEventStore,
        snapshotStore: SnapshotStore,
      ) =>
        new IngestionCQRS {
          def add(id: Key, cmd: IngestionCommand)(using
            Cmd: IngestionCommandHandler,
          ): RIO[ZConnectionPool & CQRSPersistence, Unit] =
            CQRSPersistence.atomically {
              val evnts = Cmd.applyCmd(cmd)

              ZIO.foreachDiscard(evnts) { e =>
                for {
                  version <- Version.gen
                  ch = Change(version = version, payload = e)
                  _ <- eventStore.save(id = id, ch)
                } yield ()
              }
            }

          def load(id: Key)(using
            Evt: IngestionEventHandler,
          ): RIO[ZConnectionPool & CQRSPersistence, Option[Ingestion]] =
            CQRSPersistence.atomically {
              val deps = (
                provider.load(id) <&> snapshotStore
                  .readSnapshot(id)
              ).map(_.tupled)

              val o = deps flatMap {
                case None =>
                  for {
                    events <- eventStore.readEvents(id)
                    inn = events map { es =>
                      (Evt.applyEvents(es), es.head, es.size)
                    }

                  } yield inn

                case Some((in, version)) =>
                  val events = eventStore
                    .readEvents(id, snapshotVersion = version)

                  events map (_.map { es =>
                    (Evt.applyEvents(in, es), es.head, es.size)
                  })

              }

              o flatMap (_.fold(ZIO.none) {
                case (inn, ch, size) if size >= Threshold =>
                  for {
                    _ <- checkpointer.save(inn)
                    _ <- snapshotStore
                      .save(id = id, version = ch.version)

                  } yield inn.some
                case (inn, _, _) => ZIO.some(inn)
              })
            }

        }

    }

}

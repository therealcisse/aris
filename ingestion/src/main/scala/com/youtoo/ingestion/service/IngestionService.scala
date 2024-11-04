package com.youtoo
package ingestion
package service

import cats.implicits.*

import com.youtoo.cqrs.service.*

import com.youtoo.ingestion.model.*
import com.youtoo.ingestion.repository.*

import zio.*

import zio.jdbc.*

import com.youtoo.ingestion.store.*
import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.*

trait IngestionService {
  def load(id: Ingestion.Id): Task[Option[Ingestion]]
  def loadMany(offset: Option[Key], limit: Long): Task[Chunk[Key]]
  def save(o: Ingestion): Task[Long]

}

object IngestionService {
  inline def loadMany(offset: Option[Key], limit: Long): RIO[IngestionService & ZConnection, Chunk[Key]] =
    ZIO.serviceWithZIO[IngestionService](_.loadMany(offset, limit))

  inline def load(id: Ingestion.Id): RIO[IngestionService, Option[Ingestion]] =
    ZIO.serviceWithZIO[IngestionService](_.load(id))

  inline def save(o: Ingestion): RIO[IngestionService & ZConnection, Long] =
    ZIO.serviceWithZIO[IngestionService](_.save(o))

  def live(): ZLayer[
    ZConnectionPool & IngestionRepository & IngestionEventStore & IngestionRepository & SnapshotStore & SnapshotStrategy.Factory,
    Throwable,
    IngestionService,
  ] =
    ZLayer.fromFunction {
      (
        repository: IngestionRepository,
        pool: ZConnectionPool,
        snapshotStore: SnapshotStore,
        eventStore: IngestionEventStore,
        factory: SnapshotStrategy.Factory,
      ) =>
        ZLayer {
          factory.create(IngestionEvent.discriminator) map { strategy =>
            new IngestionService {
              def load(id: Ingestion.Id): Task[Option[Ingestion]] =
                val key = id.asKey

                atomically {
                  val deps = (
                    repository.load(id) <&> snapshotStore.readSnapshot(key)
                  ).map(_.tupled)

                  val o = deps flatMap {
                    case None =>
                      for {
                        events <- eventStore.readEvents(key)
                        inn = events map { es =>
                          (
                            EventHandler.applyEvents(es),
                            es.toList.maxBy(_.version),
                            es.size,
                            None,
                          )
                        }

                      } yield inn

                    case Some((in, version)) =>
                      val events = eventStore.readEvents(key, snapshotVersion = version)

                      events map (_.map { es =>
                        (
                          EventHandler.applyEvents(in, es),
                          es.toList.maxBy(_.version),
                          es.size,
                          version.some,
                        )
                      })

                  }

                  o flatMap (_.fold(ZIO.none) {
                    case (inn, ch, size, version) if strategy(version, size) =>
                      (repository.save(inn) <&> snapshotStore.save(id = key, version = ch.version)) `as` inn.some

                    case (inn, _, _, _) => ZIO.some(inn)
                  })

                }.provideEnvironment(ZEnvironment(pool))

              def loadMany(offset: Option[Key], limit: Long): Task[Chunk[Key]] =
                atomically(repository.loadMany(offset, limit)).provideEnvironment(ZEnvironment(pool))

              def save(o: Ingestion): Task[Long] = atomically(repository.save(o)).provideEnvironment(ZEnvironment(pool))

            }
          }

        }

    }.flatten

}

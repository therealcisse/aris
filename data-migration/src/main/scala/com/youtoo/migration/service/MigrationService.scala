package com.youtoo
package migration
package service

import cats.implicits.*

import com.youtoo.cqrs.service.*

import com.youtoo.migration.model.*
import com.youtoo.migration.repository.*
import com.youtoo.cqrs.*

import zio.telemetry.opentelemetry.tracing.Tracing

import zio.*

import zio.jdbc.*
import com.youtoo.cqrs.store.*
import com.youtoo.migration.store.*

trait MigrationService {
  def load(id: Migration.Id): Task[Option[Migration]]
  def loadMany(offset: Option[Key], limit: Long): Task[Chunk[Key]]
  def save(o: Migration): Task[Long]

}

object MigrationService {
  inline def loadMany(offset: Option[Key], limit: Long): RIO[MigrationService & ZConnection, Chunk[Key]] =
    ZIO.serviceWithZIO[MigrationService](_.loadMany(offset, limit))

  inline def load(id: Migration.Id): RIO[MigrationService, Option[Migration]] =
    ZIO.serviceWithZIO[MigrationService](_.load(id))

  inline def save(o: Migration): RIO[MigrationService & ZConnection, Long] =
    ZIO.serviceWithZIO[MigrationService](_.save(o))

  def live(): ZLayer[
    ZConnectionPool & MigrationEventStore & MigrationRepository & SnapshotStore & SnapshotStrategy.Factory & Tracing,
    Throwable,
    MigrationService,
  ] =
    ZLayer.fromFunction {
      (
        repository: MigrationRepository,
        pool: ZConnectionPool,
        snapshotStore: SnapshotStore,
        eventStore: MigrationEventStore,
        factory: SnapshotStrategy.Factory,
        tracing: Tracing,
      ) =>
        ZLayer {

          factory.create(MigrationEvent.discriminator) map { strategy =>
            new MigrationServiceLive(repository, pool, snapshotStore, eventStore, strategy).traced(tracing)
          }

        }

    }.flatten

  class MigrationServiceLive(
    repository: MigrationRepository,
    pool: ZConnectionPool,
    snapshotStore: SnapshotStore,
    eventStore: MigrationEventStore,
    strategy: SnapshotStrategy,
  ) extends MigrationService { self =>
    def load(id: Migration.Id): Task[Option[Migration]] =
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

    def save(o: Migration): Task[Long] = atomically(repository.save(o)).provideEnvironment(ZEnvironment(pool))

    def traced(tracing: Tracing): MigrationService =
      new MigrationService {
        def load(id: Migration.Id): Task[Option[Migration]] =
          self.load(id) @@ tracing.aspects.span("MigrationService.load")
        def loadMany(offset: Option[Key], limit: Long): Task[Chunk[Key]] =
          self.loadMany(offset, limit) @@ tracing.aspects.span("MigrationService.loadMany")
        def save(o: Migration): Task[Long] = self.save(o) @@ tracing.aspects.span("MigrationService.save")
      }

  }

}

package com.youtoo
package migration
package store

import com.youtoo.cqrs.*
import com.youtoo.migration.model.*

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.store.*

import zio.*
import zio.prelude.*

import zio.jdbc.*
import com.youtoo.cqrs.service.*

import com.youtoo.cqrs.Codecs.*

trait MigrationEventStore extends EventStore[MigrationEvent] {}

object MigrationEventStore {
  val Table = Catalog.named("migration_log")

  def live(): ZLayer[CQRSPersistence, Throwable, MigrationEventStore] =
    ZLayer.fromFunction { (persistence: CQRSPersistence) =>
      new MigrationEventStoreLive(persistence)
    }

  class MigrationEventStoreLive(persistence: CQRSPersistence) extends MigrationEventStore {
    def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[MigrationEvent]]]] =
      persistence.readEvents[MigrationEvent](id, MigrationEvent.discriminator, Table).map { es =>
        NonEmptyList.fromIterableOption(es)
      }

    def readEvents(
      id: Key,
      snapshotVersion: Version,
    ): RIO[ZConnection, Option[NonEmptyList[Change[MigrationEvent]]]] =
      persistence.readEvents[MigrationEvent](id, MigrationEvent.discriminator, snapshotVersion, Table).map { es =>
        NonEmptyList.fromIterableOption(es)
      }

    def readEvents(
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Option[NonEmptyList[Change[MigrationEvent]]]] =
      persistence.readEvents[MigrationEvent](MigrationEvent.discriminator, query, options, Table).map { es =>
        NonEmptyList.fromIterableOption(es)
      }

    def save(id: Key, event: Change[MigrationEvent]): RIO[ZConnection, Long] =
      persistence.saveEvent(id, MigrationEvent.discriminator, event, Table)

  }
}

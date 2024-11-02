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

  def live(): ZLayer[CQRSPersistence, Throwable, MigrationEventStore] =
    ZLayer.fromFunction { (persistence: CQRSPersistence) =>
      new MigrationEventStore {
        def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[MigrationEvent]]]] =
          persistence.readEvents[MigrationEvent](id, MigrationEvent.discriminator).map { es =>
            NonEmptyList.fromIterableOption(es)
          }

        def readEvents(
          id: Key,
          snapshotVersion: Version,
        ): RIO[ZConnection, Option[NonEmptyList[Change[MigrationEvent]]]] =
          persistence.readEvents[MigrationEvent](id, MigrationEvent.discriminator, snapshotVersion).map { es =>
            NonEmptyList.fromIterableOption(es)
          }

        def readEvents(
          ns: Option[NonEmptyList[Namespace]],
          hierarchy: Option[Hierarchy],
          props: Option[NonEmptyList[EventProperty]],
        ): RIO[ZConnection, Option[NonEmptyList[Change[MigrationEvent]]]] =
          persistence.readEvents[MigrationEvent](MigrationEvent.discriminator, ns, hierarchy, props).map { es =>
            NonEmptyList.fromIterableOption(es)
          }

        def readEvents(
          snapshotVersion: Version,
          ns: Option[NonEmptyList[Namespace]],
          hierarchy: Option[Hierarchy],
          props: Option[NonEmptyList[EventProperty]],
        ): RIO[ZConnection, Option[NonEmptyList[Change[MigrationEvent]]]] =
          persistence
            .readEvents[MigrationEvent](MigrationEvent.discriminator, snapshotVersion, ns, hierarchy, props)
            .map { es =>
              NonEmptyList.fromIterableOption(es)
            }

        def save(id: Key, event: Change[MigrationEvent]): RIO[ZConnection, Long] =
          persistence.saveEvent(id, MigrationEvent.discriminator, event)

      }
    }

}

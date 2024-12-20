package com.youtoo
package migration
package store

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.migration.model.*

import zio.mock.*

import zio.*
import zio.prelude.*

object MockMigrationEventStore extends Mock[MigrationEventStore] {

  object ReadEvents {
    object Full extends Effect[Key, Throwable, Option[NonEmptyList[Change[MigrationEvent]]]]
    object Snapshot extends Effect[(Key, Version), Throwable, Option[NonEmptyList[Change[MigrationEvent]]]]

    object FullArgs
        extends Effect[
          (PersistenceQuery, FetchOptions),
          Throwable,
          Option[
            NonEmptyList[Change[MigrationEvent]],
          ],
        ]

    object FullArgsByAggregate
        extends Effect[
          (
            Key,
            PersistenceQuery,
            FetchOptions,
          ),
          Throwable,
          Option[
            NonEmptyList[Change[MigrationEvent]],
          ],
        ]
  }

  object Save extends Effect[(Key, Change[MigrationEvent]), Throwable, Long]

  val compose: URLayer[Proxy, MigrationEventStore] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new MigrationEventStore {
        def readEvents(id: Key): Task[Option[NonEmptyList[Change[MigrationEvent]]]] =
          proxy(ReadEvents.Full, id)

        def readEvents(id: Key, snapshotVersion: Version): Task[Option[NonEmptyList[Change[MigrationEvent]]]] =
          proxy(ReadEvents.Snapshot, (id, snapshotVersion))

        def readEvents(
          query: PersistenceQuery,
          options: FetchOptions,
        ): Task[Option[NonEmptyList[Change[MigrationEvent]]]] =
          proxy(ReadEvents.FullArgs, (query, options))

        def readEvents(
          id: Key,
          query: PersistenceQuery,
          options: FetchOptions,
        ): Task[Option[NonEmptyList[Change[MigrationEvent]]]] =
          proxy(ReadEvents.FullArgsByAggregate, (id, query, options))

        def save(id: Key, event: Change[MigrationEvent]): Task[Long] =
          proxy(Save, (id, event))
      }

    }
}

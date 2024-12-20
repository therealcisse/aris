package com.youtoo
package ingestion
package store

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.ingestion.model.*

import zio.mock.*

import zio.*
import zio.prelude.*

object MockIngestionEventStore extends Mock[IngestionEventStore] {

  object ReadEvents {
    object Full extends Effect[Key, Throwable, Option[NonEmptyList[Change[IngestionEvent]]]]
    object Snapshot extends Effect[(Key, Version), Throwable, Option[NonEmptyList[Change[IngestionEvent]]]]

    object FullArgs
        extends Effect[
          (PersistenceQuery, FetchOptions),
          Throwable,
          Option[
            NonEmptyList[Change[IngestionEvent]],
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
            NonEmptyList[Change[IngestionEvent]],
          ],
        ]
  }

  object Save extends Effect[(Key, Change[IngestionEvent]), Throwable, Long]

  val compose: URLayer[Proxy, IngestionEventStore] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new IngestionEventStore {
        def readEvents(id: Key): Task[Option[NonEmptyList[Change[IngestionEvent]]]] =
          proxy(ReadEvents.Full, id)

        def readEvents(id: Key, snapshotVersion: Version): Task[Option[NonEmptyList[Change[IngestionEvent]]]] =
          proxy(ReadEvents.Snapshot, (id, snapshotVersion))

        def readEvents(
          query: PersistenceQuery,
          options: FetchOptions,
        ): Task[Option[NonEmptyList[Change[IngestionEvent]]]] =
          proxy(ReadEvents.FullArgs, (query, options))

        def readEvents(
          id: Key,
          query: PersistenceQuery,
          options: FetchOptions,
        ): Task[Option[NonEmptyList[Change[IngestionEvent]]]] =
          proxy(ReadEvents.FullArgsByAggregate, (id, query, options))

        def save(id: Key, event: Change[IngestionEvent]): Task[Long] =
          proxy(Save, (id, event))
      }

    }
}

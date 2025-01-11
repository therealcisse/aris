package com.youtoo
package ingestion
package store

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.ingestion.model.*
import zio.mock.*
import zio.*
import zio.prelude.*
import zio.jdbc.*

object IngestionConfigEventStoreMock extends Mock[IngestionConfigEventStore] {

  object ReadEventsById extends Effect[Key, Throwable, Option[NonEmptyList[Change[IngestionConfigEvent]]]]
  object ReadEventsByIdAndVersion
      extends Effect[(Key, Version), Throwable, Option[NonEmptyList[Change[IngestionConfigEvent]]]]
  object ReadEventsByQuery
      extends Effect[(PersistenceQuery, FetchOptions), Throwable, Option[NonEmptyList[Change[IngestionConfigEvent]]]]
  object ReadEventsByQueryAggregate
      extends Effect[(Key, PersistenceQuery, FetchOptions), Throwable, Option[
        NonEmptyList[Change[IngestionConfigEvent]],
      ]]
  object SaveEvent extends Effect[(Key, Change[IngestionConfigEvent]), Throwable, Long]

  val compose: URLayer[Proxy, IngestionConfigEventStore] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new IngestionConfigEventStore {
        def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[IngestionConfigEvent]]]] =
          proxy(ReadEventsById, id)

        def readEvents(
          id: Key,
          snapshotVersion: Version,
        ): RIO[ZConnection, Option[NonEmptyList[Change[IngestionConfigEvent]]]] =
          proxy(ReadEventsByIdAndVersion, (id, snapshotVersion))

        def readEvents(
          query: PersistenceQuery,
          options: FetchOptions,
        ): RIO[ZConnection, Option[NonEmptyList[Change[IngestionConfigEvent]]]] =
          proxy(ReadEventsByQuery, (query, options))

        def readEvents(
          id: Key,
          query: PersistenceQuery,
          options: FetchOptions,
        ): RIO[ZConnection, Option[NonEmptyList[Change[IngestionConfigEvent]]]] =
          proxy(ReadEventsByQueryAggregate, (id, query, options))

        def save(id: Key, event: Change[IngestionConfigEvent]): RIO[ZConnection, Long] =
          proxy(SaveEvent, (id, event))
      }
    }
}

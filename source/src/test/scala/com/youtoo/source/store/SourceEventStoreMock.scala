package com.youtoo
package source
package store

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.source.model.*

import zio.mock.*
import zio.*
import zio.prelude.*
import zio.jdbc.*

object SourceEventStoreMock extends Mock[SourceEventStore] {

  object ReadEventsById extends Effect[Key, Throwable, Option[NonEmptyList[Change[SourceEvent]]]]
  object ReadEventsByIdAndVersion extends Effect[(Key, Version), Throwable, Option[NonEmptyList[Change[SourceEvent]]]]
  object ReadEventsByQuery
      extends Effect[(PersistenceQuery, FetchOptions), Throwable, Option[NonEmptyList[Change[SourceEvent]]]]
  object ReadEventsByQueryAggregate
      extends Effect[(Key, PersistenceQuery, FetchOptions), Throwable, Option[NonEmptyList[Change[SourceEvent]]]]
  object SaveEvent extends Effect[(Key, Change[SourceEvent]), Throwable, Long]

  val compose: URLayer[Proxy, SourceEventStore] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new SourceEventStore {
        def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[SourceEvent]]]] =
          proxy(ReadEventsById, id)

        def readEvents(id: Key, snapshotVersion: Version): RIO[ZConnection, Option[NonEmptyList[Change[SourceEvent]]]] =
          proxy(ReadEventsByIdAndVersion, (id, snapshotVersion))

        def readEvents(
          query: PersistenceQuery,
          options: FetchOptions,
        ): RIO[ZConnection, Option[NonEmptyList[Change[SourceEvent]]]] =
          proxy(ReadEventsByQuery, (query, options))

        def readEvents(
          id: Key,
          query: PersistenceQuery,
          options: FetchOptions,
        ): RIO[ZConnection, Option[NonEmptyList[Change[SourceEvent]]]] =
          proxy(ReadEventsByQueryAggregate, (id, query, options))

        def save(id: Key, event: Change[SourceEvent]): RIO[ZConnection, Long] =
          proxy(SaveEvent, (id, event))
      }
    }
}

package com.youtoo
package sink
package store

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.sink.model.*
import zio.mock.*
import zio.*
import zio.prelude.*
import zio.jdbc.*

object SinkEventStoreMock extends Mock[SinkEventStore] {

  object ReadEventsById extends Effect[Key, Throwable, Option[NonEmptyList[Change[SinkEvent]]]]
  object ReadEventsByIdAndVersion extends Effect[(Key, Version), Throwable, Option[NonEmptyList[Change[SinkEvent]]]]
  object ReadEventsByQuery
      extends Effect[(PersistenceQuery, FetchOptions), Throwable, Option[NonEmptyList[Change[SinkEvent]]]]
  object ReadEventsByQueryAggregate
      extends Effect[(Key, PersistenceQuery, FetchOptions), Throwable, Option[NonEmptyList[Change[SinkEvent]]]]
  object SaveEvent extends Effect[(Key, Change[SinkEvent]), Throwable, Long]

  val compose: URLayer[Proxy, SinkEventStore] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new SinkEventStore {
        def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[SinkEvent]]]] =
          proxy(ReadEventsById, id)

        def readEvents(id: Key, snapshotVersion: Version): RIO[ZConnection, Option[NonEmptyList[Change[SinkEvent]]]] =
          proxy(ReadEventsByIdAndVersion, (id, snapshotVersion))

        def readEvents(
          query: PersistenceQuery,
          options: FetchOptions,
        ): RIO[ZConnection, Option[NonEmptyList[Change[SinkEvent]]]] =
          proxy(ReadEventsByQuery, (query, options))

        def readEvents(
          id: Key,
          query: PersistenceQuery,
          options: FetchOptions,
        ): RIO[ZConnection, Option[NonEmptyList[Change[SinkEvent]]]] =
          proxy(ReadEventsByQueryAggregate, (id, query, options))

        def save(id: Key, event: Change[SinkEvent]): RIO[ZConnection, Long] =
          proxy(SaveEvent, (id, event))
      }
    }
}

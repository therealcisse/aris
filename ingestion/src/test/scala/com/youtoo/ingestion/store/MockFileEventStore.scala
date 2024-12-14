package com.youtoo
package ingestion
package store

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.ingestion.model.*

import zio.mock.*

import zio.*
import zio.prelude.*

object MockFileEventStore extends Mock[FileEventStore] {
  object ReadEventsById extends Effect[Key, Throwable, Option[NonEmptyList[Change[FileEvent]]]]
  object ReadEventsByIdAndVersion extends Effect[(Key, Version), Throwable, Option[NonEmptyList[Change[FileEvent]]]]
  object ReadEventsByFilters
      extends Effect[
        (PersistenceQuery, FetchOptions),
        Throwable,
        Option[NonEmptyList[Change[FileEvent]]],
      ]
  object Save extends Effect[(Key, Change[FileEvent]), Throwable, Long]

  val compose: URLayer[Proxy, FileEventStore] = ZLayer.fromFunction { (proxy: Proxy) =>
    new FileEventStore {
      def readEvents(id: Key): Task[Option[NonEmptyList[Change[FileEvent]]]] = proxy(ReadEventsById, id)
      def readEvents(id: Key, snapshotVersion: Version): Task[Option[NonEmptyList[Change[FileEvent]]]] =
        proxy(ReadEventsByIdAndVersion, (id, snapshotVersion))
      def readEvents(
        query: PersistenceQuery,
        options: FetchOptions,
      ): Task[Option[NonEmptyList[Change[FileEvent]]]] = proxy(ReadEventsByFilters, (query, options))
      def save(id: Key, event: Change[FileEvent]): Task[Long] = proxy(Save, (id, event))
    }
  }
}

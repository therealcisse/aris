package com.youtoo
package job
package store

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.job.model.*
import zio.mock.*
import zio.*
import zio.prelude.*
import zio.jdbc.*

object JobEventStoreMock extends Mock[JobEventStore] {

  object ReadEventsById extends Effect[Key, Throwable, Option[NonEmptyList[Change[JobEvent]]]]
  object ReadEventsByIdAndVersion extends Effect[(Key, Version), Throwable, Option[NonEmptyList[Change[JobEvent]]]]
  object ReadEventsByQuery extends Effect[(PersistenceQuery, FetchOptions), Throwable, Option[NonEmptyList[Change[JobEvent]]]]
  object ReadEventsByVersionAndQuery extends Effect[(Version, PersistenceQuery, FetchOptions), Throwable, Option[NonEmptyList[Change[JobEvent]]]]
  object SaveEvent extends Effect[(Key, Change[JobEvent]), Throwable, Long]

  val compose: URLayer[Proxy, JobEventStore] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new JobEventStore {
        def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[JobEvent]]]] =
          proxy(ReadEventsById, id)

        def readEvents(id: Key, snapshotVersion: Version): RIO[ZConnection, Option[NonEmptyList[Change[JobEvent]]]] =
          proxy(ReadEventsByIdAndVersion, (id, snapshotVersion))

        def readEvents(query: PersistenceQuery, options: FetchOptions): RIO[ZConnection, Option[NonEmptyList[Change[JobEvent]]]] =
          proxy(ReadEventsByQuery, (query, options))

        def readEvents(snapshotVersion: Version, query: PersistenceQuery, options: FetchOptions): RIO[ZConnection, Option[NonEmptyList[Change[JobEvent]]]] =
          proxy(ReadEventsByVersionAndQuery, (snapshotVersion, query, options))

        def save(id: Key, event: Change[JobEvent]): RIO[ZConnection, Long] =
          proxy(SaveEvent, (id, event))
      }
    }
}


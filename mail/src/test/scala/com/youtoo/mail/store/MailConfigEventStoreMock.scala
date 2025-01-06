package com.youtoo
package mail
package store

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.mail.model.*
import zio.mock.*
import zio.*
import zio.prelude.*
import zio.jdbc.*

object MailConfigEventStoreMock extends Mock[MailConfigEventStore] {

  object ReadEventsById extends Effect[Key, Throwable, Option[NonEmptyList[Change[MailConfigEvent]]]]
  object ReadEventsByIdAndVersion
      extends Effect[(Key, Version), Throwable, Option[NonEmptyList[Change[MailConfigEvent]]]]
  object ReadEventsByQuery
      extends Effect[(PersistenceQuery, FetchOptions), Throwable, Option[NonEmptyList[Change[MailConfigEvent]]]]
  object ReadEventsByQueryAggregate
      extends Effect[(Key, PersistenceQuery, FetchOptions), Throwable, Option[NonEmptyList[Change[MailConfigEvent]]]]
  object SaveEvent extends Effect[(Key, Change[MailConfigEvent]), Throwable, Long]

  val compose: URLayer[Proxy, MailConfigEventStore] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new MailConfigEventStore {
        def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[MailConfigEvent]]]] =
          proxy(ReadEventsById, id)

        def readEvents(
          id: Key,
          snapshotVersion: Version,
        ): RIO[ZConnection, Option[NonEmptyList[Change[MailConfigEvent]]]] =
          proxy(ReadEventsByIdAndVersion, (id, snapshotVersion))

        def readEvents(
          query: PersistenceQuery,
          options: FetchOptions,
        ): RIO[ZConnection, Option[NonEmptyList[Change[MailConfigEvent]]]] =
          proxy(ReadEventsByQuery, (query, options))

        def readEvents(
          id: Key,
          query: PersistenceQuery,
          options: FetchOptions,
        ): RIO[ZConnection, Option[NonEmptyList[Change[MailConfigEvent]]]] =
          proxy(ReadEventsByQueryAggregate, (id, query, options))

        def save(id: Key, event: Change[MailConfigEvent]): RIO[ZConnection, Long] =
          proxy(SaveEvent, (id, event))
      }
    }
}

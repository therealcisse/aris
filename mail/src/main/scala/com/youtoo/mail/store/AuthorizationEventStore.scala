package com.youtoo
package mail
package store

import com.youtoo.mail.model.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.store.*

import zio.*
import zio.prelude.*

import zio.jdbc.*
import com.youtoo.cqrs.service.*

import com.youtoo.cqrs.Codecs.*

trait AuthorizationEventStore extends EventStore[AuthorizationEvent] {}

object AuthorizationEventStore {
  val Table = Catalog.named("authorization_log")

  def live(): ZLayer[CQRSPersistence, Throwable, AuthorizationEventStore] =
    ZLayer.fromFunction { (persistence: CQRSPersistence) =>
      new AuthorizationEventStore {
        def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[AuthorizationEvent]]]] =
          persistence.readEvents[AuthorizationEvent](id, AuthorizationEvent.discriminator, Table).map { es =>
            NonEmptyList.fromIterableOption(es)
          }

        def readEvents(
          id: Key,
          snapshotVersion: Version,
        ): RIO[ZConnection, Option[NonEmptyList[Change[AuthorizationEvent]]]] =
          persistence.readEvents[AuthorizationEvent](id, AuthorizationEvent.discriminator, snapshotVersion, Table).map {
            es =>
              NonEmptyList.fromIterableOption(es)
          }

        def readEvents(
          query: PersistenceQuery,
          options: FetchOptions,
        ): RIO[ZConnection, Option[NonEmptyList[Change[AuthorizationEvent]]]] =
          persistence.readEvents[AuthorizationEvent](AuthorizationEvent.discriminator, query, options, Table).map {
            es =>
              NonEmptyList.fromIterableOption(es)
          }

        def readEvents(
          id: Key,
          query: PersistenceQuery,
          options: FetchOptions,
        ): RIO[ZConnection, Option[NonEmptyList[Change[AuthorizationEvent]]]] =
          persistence.readEvents[AuthorizationEvent](id, AuthorizationEvent.discriminator, query, options, Table).map {
            es =>
              NonEmptyList.fromIterableOption(es)
          }

        def save(id: Key, event: Change[AuthorizationEvent]): RIO[ZConnection, Long] =
          persistence.saveEvent(id, AuthorizationEvent.discriminator, event, Table)

      }
    }

}

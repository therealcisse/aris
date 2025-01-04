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

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*

trait AuthorizationEventStore extends EventStore[AuthorizationEvent] {}

object AuthorizationEventStore {
  val Table = Catalog.named("authorization_log")

  def live(): ZLayer[CQRSPersistence & Tracing, Throwable, AuthorizationEventStore] =
    ZLayer.fromFunction { (persistence: CQRSPersistence, tracing: Tracing) =>
      AuthorizationEventStoreLive(persistence).traced(tracing)
    }

  class AuthorizationEventStoreLive(persistence: CQRSPersistence) extends AuthorizationEventStore { self =>
    def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[AuthorizationEvent]]]] =
      persistence
        .readEvents[AuthorizationEvent](id, AuthorizationEvent.discriminator, Table)
        .map(NonEmptyList.fromIterableOption)

    def readEvents(
      id: Key,
      snapshotVersion: Version,
    ): RIO[ZConnection, Option[NonEmptyList[Change[AuthorizationEvent]]]] =
      persistence
        .readEvents[AuthorizationEvent](id, AuthorizationEvent.discriminator, snapshotVersion, Table)
        .map(NonEmptyList.fromIterableOption)

    def readEvents(
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Option[NonEmptyList[Change[AuthorizationEvent]]]] =
      persistence
        .readEvents[AuthorizationEvent](AuthorizationEvent.discriminator, query, options, Table)
        .map(NonEmptyList.fromIterableOption)

    def readEvents(
      id: Key,
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Option[NonEmptyList[Change[AuthorizationEvent]]]] =
      persistence
        .readEvents[AuthorizationEvent](id, AuthorizationEvent.discriminator, query, options, Table)
        .map(NonEmptyList.fromIterableOption)

    def save(id: Key, event: Change[AuthorizationEvent]): RIO[ZConnection, Long] =
      persistence.saveEvent(id, AuthorizationEvent.discriminator, event, Table)

    def traced(tracing: Tracing): AuthorizationEventStore = new AuthorizationEventStore {
      def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[AuthorizationEvent]]]] =
        self.readEvents(id) @@ tracing.aspects.span(
          "AuthorizationEventStore.readEvents",
          attributes = Attributes(Attribute.long("authorizationId", id.value)),
        )

      def readEvents(
        id: Key,
        snapshotVersion: Version,
      ): RIO[ZConnection, Option[NonEmptyList[Change[AuthorizationEvent]]]] =
        self.readEvents(id, snapshotVersion) @@ tracing.aspects.span(
          "AuthorizationEventStore.readEvents.withSnapshotVersion",
          attributes = Attributes(
            Attribute.long("authorizationId", id.value),
            Attribute.long("snapshotVersion", snapshotVersion.value)
          ),
        )

      def readEvents(
        query: PersistenceQuery,
        options: FetchOptions,
      ): RIO[ZConnection, Option[NonEmptyList[Change[AuthorizationEvent]]]] =
        self.readEvents(query, options) @@ tracing.aspects.span("AuthorizationEventStore.readEvents.withQuery")

      def readEvents(
        id: Key,
        query: PersistenceQuery,
        options: FetchOptions,
      ): RIO[ZConnection, Option[NonEmptyList[Change[AuthorizationEvent]]]] =
        self.readEvents(id, query, options) @@ tracing.aspects.span(
          "AuthorizationEventStore.readEvents.withQueryAndId",
        )

      def save(id: Key, event: Change[AuthorizationEvent]): RIO[ZConnection, Long] =
        self.save(id, event) @@ tracing.aspects.span(
          "AuthorizationEventStore.save",
          attributes = Attributes(
            Attribute.long("authorizationId", id.value),
          ),
        )

    }

  }

}

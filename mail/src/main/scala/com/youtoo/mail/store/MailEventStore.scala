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

trait MailEventStore extends EventStore[MailEvent] {}

object MailEventStore {
  val Table = Catalog.named("mail_log")

  def live(): ZLayer[CQRSPersistence & Tracing, Throwable, MailEventStore] =
    ZLayer.fromFunction { (persistence: CQRSPersistence, tracing: Tracing) =>
      MailEventStoreLive(persistence).traced(tracing)
    }

  final class MailEventStoreLive(persistence: CQRSPersistence) extends MailEventStore { self =>
    def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[MailEvent]]]] =
      persistence.readEvents[MailEvent](id, MailEvent.discriminator, Table).map(NonEmptyList.fromIterableOption)

    def readEvents(
      id: Key,
      snapshotVersion: Version,
    ): RIO[ZConnection, Option[NonEmptyList[Change[MailEvent]]]] =
      persistence
        .readEvents[MailEvent](id, MailEvent.discriminator, snapshotVersion, Table)
        .map(NonEmptyList.fromIterableOption)

    def readEvents(
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Option[NonEmptyList[Change[MailEvent]]]] =
      persistence
        .readEvents[MailEvent](MailEvent.discriminator, query, options, Table)
        .map(NonEmptyList.fromIterableOption)

    def readEvents(
      id: Key,
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Option[NonEmptyList[Change[MailEvent]]]] =
      persistence
        .readEvents[MailEvent](id, MailEvent.discriminator, query, options, Table)
        .map(NonEmptyList.fromIterableOption)

    def save(id: Key, event: Change[MailEvent]): RIO[ZConnection, Long] =
      persistence.saveEvent(id, MailEvent.discriminator, event, Table)

    def traced(tracing: Tracing): MailEventStore = new MailEventStore {
      def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[MailEvent]]]] =
        self.readEvents(id) @@ tracing.aspects.span(
          "MailEventStore.readEvents",
          attributes = Attributes(Attribute.long("mailId", id.value)),
        )

      def readEvents(
        id: Key,
        snapshotVersion: Version,
      ): RIO[ZConnection, Option[NonEmptyList[Change[MailEvent]]]] =
        self.readEvents(id, snapshotVersion) @@ tracing.aspects.span(
          "MailEventStore.readEvents.withSnapshotVersion",
          attributes =
            Attributes(
              Attribute.long("mailId", id.value),
              Attribute.long("snapshotVersion", snapshotVersion.value)
            ),
        )

      def readEvents(
        query: PersistenceQuery,
        options: FetchOptions,
      ): RIO[ZConnection, Option[NonEmptyList[Change[MailEvent]]]] =
        self.readEvents(query, options) @@ tracing.aspects.span(
          "MailEventStore.readEvents.withQuery",
        )

      def readEvents(
        id: Key,
        query: PersistenceQuery,
        options: FetchOptions,
      ): RIO[ZConnection, Option[NonEmptyList[Change[MailEvent]]]] =
        self.readEvents(id, query, options) @@ tracing.aspects.span(
          "MailEventStore.readEvents.withQueryAndId",
        )

      def save(id: Key, event: Change[MailEvent]): RIO[ZConnection, Long] =
        self.save(id, event) @@ tracing.aspects.span(
          "MailEventStore.save",
          attributes = Attributes(
            Attribute.long("mailId", id.value),
          ),
        )
    }
  }
}

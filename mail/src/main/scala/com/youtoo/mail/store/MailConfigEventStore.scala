package com.youtoo
package mail
package store

import com.youtoo.cqrs.Codecs.given
import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.service.*
import com.youtoo.cqrs.*
import com.youtoo.mail.model.*
import zio.*
import zio.jdbc.*
import zio.prelude.*
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*

trait MailConfigEventStore extends EventStore[MailConfigEvent]

object MailConfigEventStore {
  val Table = Catalog.named("mail_config_log")

  def live(): ZLayer[CQRSPersistence & Tracing, Throwable, MailConfigEventStore] =
    ZLayer.fromFunction { (persistence: CQRSPersistence, tracing: Tracing) =>
      MailConfigEventStoreLive(persistence).traced(tracing)
    }

  class MailConfigEventStoreLive(persistence: CQRSPersistence) extends MailConfigEventStore { self =>
    def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[MailConfigEvent]]]] =
      persistence
        .readEvents[MailConfigEvent](id, MailConfigEvent.discriminator, Table)
        .map(NonEmptyList.fromIterableOption)

    def readEvents(
      id: Key,
      snapshotVersion: Version,
    ): RIO[ZConnection, Option[NonEmptyList[Change[MailConfigEvent]]]] =
      persistence
        .readEvents[MailConfigEvent](id, MailConfigEvent.discriminator, snapshotVersion, Table)
        .map(NonEmptyList.fromIterableOption)

    def readEvents(
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Option[NonEmptyList[Change[MailConfigEvent]]]] =
      persistence
        .readEvents[MailConfigEvent](MailConfigEvent.discriminator, query, options, Table)
        .map(NonEmptyList.fromIterableOption)

    def readEvents(
      id: Key,
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Option[NonEmptyList[Change[MailConfigEvent]]]] =
      persistence
        .readEvents[MailConfigEvent](id, MailConfigEvent.discriminator, query, options, Table)
        .map(NonEmptyList.fromIterableOption)

    def save(id: Key, event: Change[MailConfigEvent]): RIO[ZConnection, Long] =
      persistence.saveEvent(id, MailConfigEvent.discriminator, event, Table)

    def traced(tracing: Tracing): MailConfigEventStore = new MailConfigEventStore {
      def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[MailConfigEvent]]]] =
        self.readEvents(id) @@ tracing.aspects.span(
          "MailConfigEventStore.readEvents",
          attributes = Attributes(Attribute.long("accountKey", id.value)),
        )

      def readEvents(
        id: Key,
        snapshotVersion: Version,
      ): RIO[ZConnection, Option[NonEmptyList[Change[MailConfigEvent]]]] =
        self.readEvents(id, snapshotVersion) @@ tracing.aspects.span(
          "MailConfigEventStore.readEvents.withSnapshotVersion",
          attributes = Attributes(
            Attribute.long("accountKey", id.value),
            Attribute.long("snapshotVersion", snapshotVersion.value),
          ),
        )

      def readEvents(
        query: PersistenceQuery,
        options: FetchOptions,
      ): RIO[ZConnection, Option[NonEmptyList[Change[MailConfigEvent]]]] =
        self.readEvents(query, options) @@ tracing.aspects.span("MailConfigEventStore.readEvents.withQuery")

      def readEvents(
        id: Key,
        query: PersistenceQuery,
        options: FetchOptions,
      ): RIO[ZConnection, Option[NonEmptyList[Change[MailConfigEvent]]]] =
        self.readEvents(id, query, options) @@ tracing.aspects.span("MailConfigEventStore.readEvents.withQueryAndId")

      def save(id: Key, event: Change[MailConfigEvent]): RIO[ZConnection, Long] =
        self.save(id, event) @@ tracing.aspects.span(
          "MailConfigEventStore.save",
          attributes = Attributes(
            Attribute.long("accountKey", id.value),
          ),
        )

    }
  }
}

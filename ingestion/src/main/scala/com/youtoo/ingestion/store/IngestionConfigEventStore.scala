package com.youtoo
package ingestion
package store

import com.youtoo.cqrs.Codecs.given

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.service.*
import com.youtoo.cqrs.*
import com.youtoo.ingestion.model.*

import zio.*
import zio.jdbc.*
import zio.prelude.*

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*

trait IngestionConfigEventStore extends EventStore[IngestionConfigEvent]

object IngestionConfigEventStore {
  val Table = Catalog.named("ingestion_config_log")

  def live(): ZLayer[CQRSPersistence & Tracing, Throwable, IngestionConfigEventStore] =
    ZLayer.fromFunction { (persistence: CQRSPersistence, tracing: Tracing) =>
      IngestionConfigEventStoreLive(persistence).traced(tracing)
    }

  class IngestionConfigEventStoreLive(persistence: CQRSPersistence) extends IngestionConfigEventStore { self =>
    def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[IngestionConfigEvent]]]] =
      persistence
        .readEvents[IngestionConfigEvent](id, IngestionConfigEvent.discriminator, Table)
        .map(NonEmptyList.fromIterableOption)

    def readEvents(
      id: Key,
      snapshotVersion: Version,
    ): RIO[ZConnection, Option[NonEmptyList[Change[IngestionConfigEvent]]]] =
      persistence
        .readEvents[IngestionConfigEvent](id, IngestionConfigEvent.discriminator, snapshotVersion, Table)
        .map(NonEmptyList.fromIterableOption)

    def readEvents(
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Option[NonEmptyList[Change[IngestionConfigEvent]]]] =
      persistence
        .readEvents[IngestionConfigEvent](IngestionConfigEvent.discriminator, query, options, Table)
        .map(NonEmptyList.fromIterableOption)

    def readEvents(
      id: Key,
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Option[NonEmptyList[Change[IngestionConfigEvent]]]] =
      persistence
        .readEvents[IngestionConfigEvent](id, IngestionConfigEvent.discriminator, query, options, Table)
        .map(NonEmptyList.fromIterableOption)

    def save(id: Key, event: Change[IngestionConfigEvent]): RIO[ZConnection, Long] =
      persistence.saveEvent(id, IngestionConfigEvent.discriminator, event, Table)

    def traced(tracing: Tracing): IngestionConfigEventStore = new IngestionConfigEventStore {
      def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[IngestionConfigEvent]]]] =
        self.readEvents(id) @@ tracing.aspects.span(
          "IngestionConfigEventStore.readEvents",
          attributes = Attributes(Attribute.long("ingestionId", id.value)),
        )

      def readEvents(
        id: Key,
        snapshotVersion: Version,
      ): RIO[ZConnection, Option[NonEmptyList[Change[IngestionConfigEvent]]]] =
        self.readEvents(id, snapshotVersion) @@ tracing.aspects.span(
          "IngestionConfigEventStore.readEvents.withSnapshotVersion",
          attributes = Attributes(
            Attribute.long("ingestionId", id.value),
            Attribute.long("snapshotVersion", snapshotVersion.value),
          ),
        )

      def readEvents(
        query: PersistenceQuery,
        options: FetchOptions,
      ): RIO[ZConnection, Option[NonEmptyList[Change[IngestionConfigEvent]]]] =
        self.readEvents(query, options) @@ tracing.aspects.span("IngestionConfigEventStore.readEvents.withQuery")

      def readEvents(
        id: Key,
        query: PersistenceQuery,
        options: FetchOptions,
      ): RIO[ZConnection, Option[NonEmptyList[Change[IngestionConfigEvent]]]] =
        self.readEvents(id, query, options) @@ tracing.aspects.span(
          "IngestionConfigEventStore.readEvents.withQueryAndId",
        )

      def save(id: Key, event: Change[IngestionConfigEvent]): RIO[ZConnection, Long] =
        self.save(id, event) @@ tracing.aspects.span(
          "IngestionConfigEventStore.save",
          attributes = Attributes(
            Attribute.long("ingestionId", id.value),
          ),
        )

    }
  }

}

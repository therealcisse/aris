package com.youtoo
package ingestion
package store

import com.youtoo.ingestion.model.*

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

trait IngestionEventStore extends EventStore[IngestionEvent] {}

object IngestionEventStore {
  val Table = Catalog.named("ingestion_log")

  def live(): ZLayer[CQRSPersistence & Tracing, Throwable, IngestionEventStore] =
    ZLayer.fromFunction { (persistence: CQRSPersistence, tracing: Tracing) =>
      IngestionEventStoreLive(persistence).traced(tracing)
    }

  final class IngestionEventStoreLive(persistence: CQRSPersistence) extends IngestionEventStore { self =>
    def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[IngestionEvent]]]] =
      persistence
        .readEvents[IngestionEvent](id, IngestionEvent.discriminator, Table)
        .map(NonEmptyList.fromIterableOption)

    def readEvents(
      id: Key,
      snapshotVersion: Version,
    ): RIO[ZConnection, Option[NonEmptyList[Change[IngestionEvent]]]] =
      persistence
        .readEvents[IngestionEvent](id, IngestionEvent.discriminator, snapshotVersion, Table)
        .map(NonEmptyList.fromIterableOption)

    def readEvents(
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Option[NonEmptyList[Change[IngestionEvent]]]] =
      persistence
        .readEvents[IngestionEvent](IngestionEvent.discriminator, query, options, Table)
        .map(NonEmptyList.fromIterableOption)

    def readEvents(
      id: Key,
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Option[NonEmptyList[Change[IngestionEvent]]]] =
      persistence
        .readEvents[IngestionEvent](id, IngestionEvent.discriminator, query, options, Table)
        .map(NonEmptyList.fromIterableOption)

    def save(id: Key, event: Change[IngestionEvent]): RIO[ZConnection, Long] =
      persistence.saveEvent(id, IngestionEvent.discriminator, event, Table)

    def traced(tracing: Tracing): IngestionEventStore = new IngestionEventStore {
      def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[IngestionEvent]]]] =
        self.readEvents(id) @@ tracing.aspects.span(
          "IngestionEventStore.readEvents",
          attributes = Attributes(Attribute.long("ingestionId", id.value)),
        )

      def readEvents(
        id: Key,
        snapshotVersion: Version,
      ): RIO[ZConnection, Option[NonEmptyList[Change[IngestionEvent]]]] =
        self.readEvents(id, snapshotVersion) @@ tracing.aspects.span(
          "IngestionEventStore.readEvents.withSnapshotVersion",
          attributes = Attributes(
            Attribute.long("ingestionId", id.value),
            Attribute.long("snapshotVersion", snapshotVersion.value),
          ),
        )

      def readEvents(
        query: PersistenceQuery,
        options: FetchOptions,
      ): RIO[ZConnection, Option[NonEmptyList[Change[IngestionEvent]]]] =
        self.readEvents(query, options) @@ tracing.aspects.span("IngestionEventStore.readEvents.withQuery")

      def readEvents(
        id: Key,
        query: PersistenceQuery,
        options: FetchOptions,
      ): RIO[ZConnection, Option[NonEmptyList[Change[IngestionEvent]]]] =
        self.readEvents(id, query, options) @@ tracing.aspects.span(
          "IngestionEventStore.readEvents.withQueryAndId",
        )

      def save(id: Key, event: Change[IngestionEvent]): RIO[ZConnection, Long] =
        self.save(id, event) @@ tracing.aspects.span(
          "IngestionEventStore.save",
          attributes = Attributes(
            Attribute.long("ingestionId", id.value),
          ),
        )

    }
  }

}

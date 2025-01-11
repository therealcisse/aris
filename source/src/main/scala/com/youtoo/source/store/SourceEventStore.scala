package com.youtoo
package source
package store

import com.youtoo.cqrs.Codecs.given

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.service.*
import com.youtoo.cqrs.*

import com.youtoo.source.model.*

import zio.*
import zio.jdbc.*
import zio.prelude.*

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*

trait SourceEventStore extends EventStore[SourceEvent]

object SourceEventStore {
  val Table = Catalog.named("source_log")

  def live(): ZLayer[CQRSPersistence & Tracing, Throwable, SourceEventStore] =
    ZLayer.fromFunction { (persistence: CQRSPersistence, tracing: Tracing) =>
      SourceEventStoreLive(persistence).traced(tracing)
    }

  class SourceEventStoreLive(persistence: CQRSPersistence) extends SourceEventStore { self =>
    def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[SourceEvent]]]] =
      persistence.readEvents[SourceEvent](id, SourceEvent.discriminator, Table).map(NonEmptyList.fromIterableOption)

    def readEvents(
      id: Key,
      snapshotVersion: Version,
    ): RIO[ZConnection, Option[NonEmptyList[Change[SourceEvent]]]] =
      persistence
        .readEvents[SourceEvent](id, SourceEvent.discriminator, snapshotVersion, Table)
        .map(NonEmptyList.fromIterableOption)

    def readEvents(
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Option[NonEmptyList[Change[SourceEvent]]]] =
      persistence
        .readEvents[SourceEvent](SourceEvent.discriminator, query, options, Table)
        .map(NonEmptyList.fromIterableOption)

    def readEvents(
      id: Key,
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Option[NonEmptyList[Change[SourceEvent]]]] =
      persistence
        .readEvents[SourceEvent](id, SourceEvent.discriminator, query, options, Table)
        .map(NonEmptyList.fromIterableOption)

    def save(id: Key, event: Change[SourceEvent]): RIO[ZConnection, Long] =
      persistence.saveEvent(id, SourceEvent.discriminator, event, Table)

    def traced(tracing: Tracing): SourceEventStore = new SourceEventStore {
      def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[SourceEvent]]]] =
        self.readEvents(id) @@ tracing.aspects.span(
          "SourceEventStore.readEvents",
          attributes = Attributes(Attribute.long("sourceId", id.value)),
        )

      def readEvents(
        id: Key,
        snapshotVersion: Version,
      ): RIO[ZConnection, Option[NonEmptyList[Change[SourceEvent]]]] =
        self.readEvents(id, snapshotVersion) @@ tracing.aspects.span(
          "SourceEventStore.readEvents.withSnapshotVersion",
          attributes = Attributes(
            Attribute.long("sourceId", id.value),
            Attribute.long("snapshotVersion", snapshotVersion.value),
          ),
        )

      def readEvents(
        query: PersistenceQuery,
        options: FetchOptions,
      ): RIO[ZConnection, Option[NonEmptyList[Change[SourceEvent]]]] =
        self.readEvents(query, options) @@ tracing.aspects.span("SourceEventStore.readEvents.withQuery")

      def readEvents(
        id: Key,
        query: PersistenceQuery,
        options: FetchOptions,
      ): RIO[ZConnection, Option[NonEmptyList[Change[SourceEvent]]]] =
        self.readEvents(id, query, options) @@ tracing.aspects.span("SourceEventStore.readEvents.withQueryAndId")

      def save(id: Key, event: Change[SourceEvent]): RIO[ZConnection, Long] =
        self.save(id, event) @@ tracing.aspects.span(
          "SourceEventStore.save",
          attributes = Attributes(
            Attribute.long("sourceId", id.value),
          ),
        )

    }
  }

}

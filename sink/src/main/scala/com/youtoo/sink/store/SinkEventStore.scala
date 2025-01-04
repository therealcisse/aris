package com.youtoo
package sink
package store

import com.youtoo.cqrs.Codecs.given

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.service.*
import com.youtoo.cqrs.*

import com.youtoo.sink.model.*

import zio.*
import zio.jdbc.*
import zio.prelude.*

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*

trait SinkEventStore extends EventStore[SinkEvent]

object SinkEventStore {
  val Table = Catalog.named("sink_log")

  def live(): ZLayer[CQRSPersistence & Tracing, Throwable, SinkEventStore] =
    ZLayer.fromFunction { (persistence: CQRSPersistence, tracing: Tracing) =>
      SinkEventStoreLive(persistence).traced(tracing)
    }

  class SinkEventStoreLive(persistence: CQRSPersistence) extends SinkEventStore { self =>
    def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[SinkEvent]]]] =
      persistence.readEvents[SinkEvent](id, SinkEvent.discriminator, Table).map(NonEmptyList.fromIterableOption)

    def readEvents(
      id: Key,
      snapshotVersion: Version,
    ): RIO[ZConnection, Option[NonEmptyList[Change[SinkEvent]]]] =
      persistence
        .readEvents[SinkEvent](id, SinkEvent.discriminator, snapshotVersion, Table)
        .map(NonEmptyList.fromIterableOption)

    def readEvents(
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Option[NonEmptyList[Change[SinkEvent]]]] =
      persistence
        .readEvents[SinkEvent](SinkEvent.discriminator, query, options, Table)
        .map(NonEmptyList.fromIterableOption)

    def readEvents(
      id: Key,
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Option[NonEmptyList[Change[SinkEvent]]]] =
      persistence
        .readEvents[SinkEvent](id, SinkEvent.discriminator, query, options, Table)
        .map(NonEmptyList.fromIterableOption)

    def save(id: Key, event: Change[SinkEvent]): RIO[ZConnection, Long] =
      persistence.saveEvent(id, SinkEvent.discriminator, event, Table)

    def traced(tracing: Tracing): SinkEventStore = new SinkEventStore {
      def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[SinkEvent]]]] =
        self.readEvents(id) @@ tracing.aspects.span(
          "SinkEventStore.readEvents",
          attributes = Attributes(Attribute.long("sinkId", id.value)),
        )

      def readEvents(
        id: Key,
        snapshotVersion: Version,
      ): RIO[ZConnection, Option[NonEmptyList[Change[SinkEvent]]]] =
        self.readEvents(id, snapshotVersion) @@ tracing.aspects.span(
          "SinkEventStore.readEvents.withSnapshotVersion",
          attributes =
            Attributes(
              Attribute.long("sinkId", id.value),
              Attribute.long("snapshotVersion", snapshotVersion.value)
            ),
        )

      def readEvents(
        query: PersistenceQuery,
        options: FetchOptions,
      ): RIO[ZConnection, Option[NonEmptyList[Change[SinkEvent]]]] =
        self.readEvents(query, options) @@ tracing.aspects.span("SinkEventStore.readEvents.withQuery")

      def readEvents(
        id: Key,
        query: PersistenceQuery,
        options: FetchOptions,
      ): RIO[ZConnection, Option[NonEmptyList[Change[SinkEvent]]]] =
        self.readEvents(id, query, options) @@ tracing.aspects.span("SinkEventStore.readEvents.withQueryAndId")

      def save(id: Key, event: Change[SinkEvent]): RIO[ZConnection, Long] =
        self.save(id, event) @@ tracing.aspects.span(
          "SinkEventStore.save",
          attributes = Attributes(
            Attribute.long("sinkId", id.value),
          ),
        )

    }
  }

}

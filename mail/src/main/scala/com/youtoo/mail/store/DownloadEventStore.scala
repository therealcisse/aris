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

trait DownloadEventStore extends EventStore[DownloadEvent] {}

object DownloadEventStore {
  val Table = Catalog.named("download_log")

  def live(): ZLayer[CQRSPersistence & Tracing, Throwable, DownloadEventStore] =
    ZLayer.fromFunction { (persistence: CQRSPersistence, tracing: Tracing) =>
      DownloadEventStoreLive(persistence).traced(tracing)
    }

  class DownloadEventStoreLive(persistence: CQRSPersistence) extends DownloadEventStore { self =>
    def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[DownloadEvent]]]] =
      persistence.readEvents[DownloadEvent](id, DownloadEvent.discriminator, Table).map(NonEmptyList.fromIterableOption)

    def readEvents(
      id: Key,
      snapshotVersion: Version,
    ): RIO[ZConnection, Option[NonEmptyList[Change[DownloadEvent]]]] =
      persistence
        .readEvents[DownloadEvent](id, DownloadEvent.discriminator, snapshotVersion, Table)
        .map(NonEmptyList.fromIterableOption)

    def readEvents(
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Option[NonEmptyList[Change[DownloadEvent]]]] =
      persistence
        .readEvents[DownloadEvent](DownloadEvent.discriminator, query, options, Table)
        .map(NonEmptyList.fromIterableOption)

    def readEvents(
      id: Key,
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Option[NonEmptyList[Change[DownloadEvent]]]] =
      persistence
        .readEvents[DownloadEvent](id, DownloadEvent.discriminator, query, options, Table)
        .map(NonEmptyList.fromIterableOption)

    def save(id: Key, event: Change[DownloadEvent]): RIO[ZConnection, Long] =
      persistence.saveEvent(id, DownloadEvent.discriminator, event, Table)

    def traced(tracing: Tracing): DownloadEventStore = new DownloadEventStore {
      def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[DownloadEvent]]]] =
        self.readEvents(id) @@ tracing.aspects.span(
          "DownloadEventStore.readEvents",
          attributes = Attributes(Attribute.long("downloadId", id.value)),
        )

      def readEvents(
        id: Key,
        snapshotVersion: Version,
      ): RIO[ZConnection, Option[NonEmptyList[Change[DownloadEvent]]]] =
        self.readEvents(id, snapshotVersion) @@ tracing.aspects.span(
          "DownloadEventStore.readEvents.withSnapshotVersion",
          attributes =
            Attributes(
              Attribute.long("downloadId", id.value),
              Attribute.long("snapshotVersion", snapshotVersion.value)
            ),
        )

      def readEvents(
        query: PersistenceQuery,
        options: FetchOptions,
      ): RIO[ZConnection, Option[NonEmptyList[Change[DownloadEvent]]]] =
        self.readEvents(query, options) @@ tracing.aspects.span("DownloadEventStore.readEvents.withQuery")

      def readEvents(
        id: Key,
        query: PersistenceQuery,
        options: FetchOptions,
      ): RIO[ZConnection, Option[NonEmptyList[Change[DownloadEvent]]]] =
        self.readEvents(id, query, options) @@ tracing.aspects.span(
          "DownloadEventStore.readEvents.withQueryAndId",
        )

      def save(id: Key, event: Change[DownloadEvent]): RIO[ZConnection, Long] =
        self.save(id, event) @@ tracing.aspects.span(
          "DownloadEventStore.save",
          attributes = Attributes(
            Attribute.long("downloadId", id.value),
          ),
        )

    }
  }

}

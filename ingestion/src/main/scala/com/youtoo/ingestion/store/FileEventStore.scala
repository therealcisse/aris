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

trait FileEventStore extends EventStore[FileEvent] {}

object FileEventStore {
  val Table = Catalog.named("file_log")

  def live(): ZLayer[CQRSPersistence & Tracing, Throwable, FileEventStore] =
    ZLayer.fromFunction { (persistence: CQRSPersistence, tracing: Tracing) =>
      FileEventStoreLive(persistence).traced(tracing)
    }

  class FileEventStoreLive(persistence: CQRSPersistence) extends FileEventStore { self =>
    def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[FileEvent]]]] =
      persistence.readEvents[FileEvent](id, FileEvent.discriminator, Table).map { es =>
        NonEmptyList.fromIterableOption(es)
      }

    def readEvents(
      id: Key,
      snapshotVersion: Version,
    ): RIO[ZConnection, Option[NonEmptyList[Change[FileEvent]]]] =
      persistence.readEvents[FileEvent](id, FileEvent.discriminator, snapshotVersion, Table).map { es =>
        NonEmptyList.fromIterableOption(es)
      }

    def readEvents(
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Option[NonEmptyList[Change[FileEvent]]]] =
      persistence.readEvents[FileEvent](FileEvent.discriminator, query, options, Table).map { es =>
        NonEmptyList.fromIterableOption(es)
      }

    def readEvents(
      id: Key,
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Option[NonEmptyList[Change[FileEvent]]]] =
      persistence.readEvents[FileEvent](id, FileEvent.discriminator, query, options, Table).map { es =>
        NonEmptyList.fromIterableOption(es)
      }

    def save(id: Key, event: Change[FileEvent]): RIO[ZConnection, Long] =
      persistence.saveEvent(id, FileEvent.discriminator, event, Table)

    def traced(tracing: Tracing): FileEventStore = new FileEventStore {
      def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[FileEvent]]]] =
        self.readEvents(id) @@ tracing.aspects.span(
          "FileEventStore.readEvents",
          attributes = Attributes(Attribute.long("fileId", id.value)),
        )

      def readEvents(
        id: Key,
        snapshotVersion: Version,
      ): RIO[ZConnection, Option[NonEmptyList[Change[FileEvent]]]] =
        self.readEvents(id, snapshotVersion) @@ tracing.aspects.span(
          "FileEventStore.readEvents.withSnapshotVersion",
          attributes = Attributes(
            Attribute.long("fileId", id.value),
            Attribute.long("snapshotVersion", snapshotVersion.value),
          ),
        )

      def readEvents(
        query: PersistenceQuery,
        options: FetchOptions,
      ): RIO[ZConnection, Option[NonEmptyList[Change[FileEvent]]]] =
        self.readEvents(query, options) @@ tracing.aspects.span("FileEventStore.readEvents.withQuery")

      def readEvents(
        id: Key,
        query: PersistenceQuery,
        options: FetchOptions,
      ): RIO[ZConnection, Option[NonEmptyList[Change[FileEvent]]]] =
        self.readEvents(id, query, options) @@ tracing.aspects.span("FileEventStore.readEvents.withQueryAndId")

      def save(id: Key, event: Change[FileEvent]): RIO[ZConnection, Long] =
        self.save(id, event) @@ tracing.aspects.span(
          "FileEventStore.save",
          attributes = Attributes(
            Attribute.long("fileId", id.value),
          ),
        )
    }

  }

}

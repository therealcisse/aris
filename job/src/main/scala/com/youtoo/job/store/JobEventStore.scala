package com.youtoo
package job
package store

import com.youtoo.job.model.*

import com.youtoo.cqrs.Codecs.given

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.service.*
import zio.*
import zio.prelude.*
import zio.jdbc.*

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*

trait JobEventStore extends EventStore[JobEvent]

object JobEventStore {
  val Table = Catalog.named("job_log")

  def live(): ZLayer[CQRSPersistence & Tracing, Throwable, JobEventStore] =
    ZLayer.fromFunction { (persistence: CQRSPersistence, tracing: Tracing) =>
      JobEventStoreLive(persistence).traced(tracing)
    }

  class JobEventStoreLive(persistence: CQRSPersistence) extends JobEventStore { self =>
    def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[JobEvent]]]] =
      persistence.readEvents[JobEvent](id, JobEvent.discriminator, Table).map { es =>
        NonEmptyList.fromIterableOption(es)
      }

    def readEvents(
      id: Key,
      snapshotVersion: Version,
    ): RIO[ZConnection, Option[NonEmptyList[Change[JobEvent]]]] =
      persistence.readEvents[JobEvent](id, JobEvent.discriminator, snapshotVersion, Table).map { es =>
        NonEmptyList.fromIterableOption(es)
      }

    def readEvents(
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Option[NonEmptyList[Change[JobEvent]]]] =
      persistence.readEvents[JobEvent](JobEvent.discriminator, query, options, Table).map { es =>
        NonEmptyList.fromIterableOption(es)
      }

    def readEvents(
      id: Key,
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Option[NonEmptyList[Change[JobEvent]]]] =
      persistence.readEvents[JobEvent](id, JobEvent.discriminator, query, options, Table).map { es =>
        NonEmptyList.fromIterableOption(es)
      }

    def save(id: Key, event: Change[JobEvent]): RIO[ZConnection, Long] =
      persistence.saveEvent(id, JobEvent.discriminator, event, Table)

    def traced(tracing: Tracing): JobEventStore = new JobEventStore {
      def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[JobEvent]]]] =
        self.readEvents(id) @@ tracing.aspects.span(
          "JobEventStore.readEvents",
          attributes = Attributes(Attribute.long("jobId", id.value)),
        )

      def readEvents(
        id: Key,
        snapshotVersion: Version,
      ): RIO[ZConnection, Option[NonEmptyList[Change[JobEvent]]]] =
        self.readEvents(id, snapshotVersion) @@ tracing.aspects.span(
          "JobEventStore.readEvents.withSnapshotVersion",
          attributes = Attributes(
            Attribute.long("jobId", id.value),
            Attribute.long("snapshotVersion", snapshotVersion.value),
          ),
        )

      def readEvents(
        query: PersistenceQuery,
        options: FetchOptions,
      ): RIO[ZConnection, Option[NonEmptyList[Change[JobEvent]]]] =
        self.readEvents(query, options) @@ tracing.aspects.span("JobEventStore.readEvents.withQuery")

      def readEvents(
        id: Key,
        query: PersistenceQuery,
        options: FetchOptions,
      ): RIO[ZConnection, Option[NonEmptyList[Change[JobEvent]]]] =
        self.readEvents(id, query, options) @@ tracing.aspects.span("JobEventStore.readEvents.withQueryAndId")

      def save(id: Key, event: Change[JobEvent]): RIO[ZConnection, Long] =
        self.save(id, event) @@ tracing.aspects.span(
          "JobEventStore.save",
          attributes = Attributes(
            Attribute.long("jobId", id.value),
          ),
        )

    }

  }

}

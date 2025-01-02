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

trait DownloadEventStore extends EventStore[DownloadEvent] {}

object DownloadEventStore {
  val Table = Catalog.named("download_log")

  def live(): ZLayer[CQRSPersistence, Throwable, DownloadEventStore] =
    ZLayer.fromFunction { (persistence: CQRSPersistence) =>
      new DownloadEventStore {
        def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[DownloadEvent]]]] =
          persistence.readEvents[DownloadEvent](id, DownloadEvent.discriminator, Table).map { es =>
            NonEmptyList.fromIterableOption(es)
          }

        def readEvents(
          id: Key,
          snapshotVersion: Version,
        ): RIO[ZConnection, Option[NonEmptyList[Change[DownloadEvent]]]] =
          persistence.readEvents[DownloadEvent](id, DownloadEvent.discriminator, snapshotVersion, Table).map { es =>
            NonEmptyList.fromIterableOption(es)
          }

        def readEvents(
          query: PersistenceQuery,
          options: FetchOptions,
        ): RIO[ZConnection, Option[NonEmptyList[Change[DownloadEvent]]]] =
          persistence.readEvents[DownloadEvent](DownloadEvent.discriminator, query, options, Table).map { es =>
            NonEmptyList.fromIterableOption(es)
          }

        def readEvents(
          id: Key,
          query: PersistenceQuery,
          options: FetchOptions,
        ): RIO[ZConnection, Option[NonEmptyList[Change[DownloadEvent]]]] =
          persistence.readEvents[DownloadEvent](id, DownloadEvent.discriminator, query, options, Table).map { es =>
            NonEmptyList.fromIterableOption(es)
          }

        def save(id: Key, event: Change[DownloadEvent]): RIO[ZConnection, Long] =
          persistence.saveEvent(id, DownloadEvent.discriminator, event, Table)

      }
    }

}

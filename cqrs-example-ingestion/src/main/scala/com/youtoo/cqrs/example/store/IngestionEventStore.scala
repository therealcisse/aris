package com.youtoo.cqrs
package example
package store

import com.youtoo.cqrs.example.model.*

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.store.*

import zio.*
import zio.prelude.*

import zio.jdbc.*
import com.youtoo.cqrs.service.*

import com.youtoo.cqrs.Codecs.*

trait IngestionEventStore extends EventStore[IngestionEvent] {}

object IngestionEventStore {

  def live(): ZLayer[CQRSPersistence, Throwable, IngestionEventStore] =
    ZLayer.fromFunction { (persistence: CQRSPersistence) =>
      new IngestionEventStore {
        def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[IngestionEvent]]]] =
          persistence.readEvents[IngestionEvent](id, IngestionEvent.discriminator).map { es =>
            NonEmptyList.fromIterableOption(es)
          }

        def readEvents(
          id: Key,
          snapshotVersion: Version,
        ): RIO[ZConnection, Option[NonEmptyList[Change[IngestionEvent]]]] =
          persistence.readEvents[IngestionEvent](id, IngestionEvent.discriminator, snapshotVersion).map { es =>
            NonEmptyList.fromIterableOption(es)
          }

        def save(id: Key, event: Change[IngestionEvent]): RIO[ZConnection, Long] =
          persistence.saveEvent(id, IngestionEvent.discriminator, event)

      }
    }

}

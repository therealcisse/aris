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

trait JobEventStore extends EventStore[JobEvent]

object JobEventStore {
  def live(): ZLayer[CQRSPersistence, Throwable, JobEventStore] =
    ZLayer.fromFunction { (persistence: CQRSPersistence) =>
      new JobEventStore {

        def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[JobEvent]]]] =
          persistence.readEvents[JobEvent](id, JobEvent.discriminator).map { es =>
            NonEmptyList.fromIterableOption(es)
          }

        def readEvents(
          id: Key,
          snapshotVersion: Version,
        ): RIO[ZConnection, Option[NonEmptyList[Change[JobEvent]]]] =
          persistence.readEvents[JobEvent](id, JobEvent.discriminator, snapshotVersion).map { es =>
            NonEmptyList.fromIterableOption(es)
          }

        def readEvents(
          query: PersistenceQuery,
          options: FetchOptions,
        ): RIO[ZConnection, Option[NonEmptyList[Change[JobEvent]]]] =
          persistence.readEvents[JobEvent](JobEvent.discriminator, query, options).map { es =>
            NonEmptyList.fromIterableOption(es)
          }

        def save(id: Key, event: Change[JobEvent]): RIO[ZConnection, Long] =
          persistence.saveEvent(id, JobEvent.discriminator, event)
      }
    }

}

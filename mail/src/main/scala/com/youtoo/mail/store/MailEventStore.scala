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

trait MailEventStore extends EventStore[MailEvent] {}

object MailEventStore {

  def live(): ZLayer[CQRSPersistence, Throwable, MailEventStore] =
    ZLayer.fromFunction { (persistence: CQRSPersistence) =>
      new MailEventStore {
        def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[MailEvent]]]] =
          persistence.readEvents[MailEvent](id, MailEvent.discriminator).map { es =>
            NonEmptyList.fromIterableOption(es)
          }

        def readEvents(
          id: Key,
          snapshotVersion: Version,
        ): RIO[ZConnection, Option[NonEmptyList[Change[MailEvent]]]] =
          persistence.readEvents[MailEvent](id, MailEvent.discriminator, snapshotVersion).map { es =>
            NonEmptyList.fromIterableOption(es)
          }

        def readEvents(
          query: PersistenceQuery,
          options: FetchOptions,
        ): RIO[ZConnection, Option[NonEmptyList[Change[MailEvent]]]] =
          persistence.readEvents[MailEvent](MailEvent.discriminator, query, options).map { es =>
            NonEmptyList.fromIterableOption(es)
          }

        def save(id: Key, event: Change[MailEvent]): RIO[ZConnection, Long] =
          persistence.saveEvent(id, MailEvent.discriminator, event)

      }
    }

}


package com.youtoo
package mail
package store

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.mail.model.*

import zio.mock.*

import zio.*
import zio.prelude.*

object MockMailEventStore extends Mock[MailEventStore] {

  object ReadEvents {
    object Full extends Effect[Key, Throwable, Option[NonEmptyList[Change[MailEvent]]]]
    object Snapshot extends Effect[(Key, Version), Throwable, Option[NonEmptyList[Change[MailEvent]]]]

    object FullArgs
        extends Effect[
          (PersistenceQuery, FetchOptions),
          Throwable,
          Option[
            NonEmptyList[Change[MailEvent]],
          ],
        ]
  }

  object Save extends Effect[(Key, Change[MailEvent]), Throwable, Long]

  val compose: URLayer[Proxy, MailEventStore] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new MailEventStore {
        def readEvents(id: Key): Task[Option[NonEmptyList[Change[MailEvent]]]] =
          proxy(ReadEvents.Full, id)

        def readEvents(id: Key, snapshotVersion: Version): Task[Option[NonEmptyList[Change[MailEvent]]]] =
          proxy(ReadEvents.Snapshot, (id, snapshotVersion))

        def readEvents(
          query: PersistenceQuery,
          options: FetchOptions,
        ): Task[Option[NonEmptyList[Change[MailEvent]]]] =
          proxy(ReadEvents.FullArgs, (query, options))

        def save(id: Key, event: Change[MailEvent]): Task[Long] =
          proxy(Save, (id, event))
      }

    }
}

package com.youtoo
package mail
package store

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.mail.model.*

import zio.mock.*

import zio.*
import zio.prelude.*

object MockAuthorizationEventStore extends Mock[AuthorizationEventStore] {

  object ReadEvents {
    object Full extends Effect[Key, Throwable, Option[NonEmptyList[Change[AuthorizationEvent]]]]
    object Snapshot extends Effect[(Key, Version), Throwable, Option[NonEmptyList[Change[AuthorizationEvent]]]]

    object FullArgs
        extends Effect[
          (PersistenceQuery, FetchOptions),
          Throwable,
          Option[
            NonEmptyList[Change[AuthorizationEvent]],
          ],
        ]

    object FullArgsByAggregate
        extends Effect[
          (
            Key,
            PersistenceQuery,
            FetchOptions,
          ),
          Throwable,
          Option[
            NonEmptyList[Change[AuthorizationEvent]],
          ],
        ]
  }

  object Save extends Effect[(Key, Change[AuthorizationEvent]), Throwable, Long]

  val compose: URLayer[Proxy, AuthorizationEventStore] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new AuthorizationEventStore {
        def readEvents(id: Key): Task[Option[NonEmptyList[Change[AuthorizationEvent]]]] =
          proxy(ReadEvents.Full, id)

        def readEvents(id: Key, snapshotVersion: Version): Task[Option[NonEmptyList[Change[AuthorizationEvent]]]] =
          proxy(ReadEvents.Snapshot, (id, snapshotVersion))

        def readEvents(
          query: PersistenceQuery,
          options: FetchOptions,
        ): Task[Option[NonEmptyList[Change[AuthorizationEvent]]]] =
          proxy(ReadEvents.FullArgs, (query, options))

        def readEvents(
          id: Key,
          query: PersistenceQuery,
          options: FetchOptions,
        ): Task[Option[NonEmptyList[Change[AuthorizationEvent]]]] =
          proxy(ReadEvents.FullArgsByAggregate, (id, query, options))

        def save(id: Key, event: Change[AuthorizationEvent]): Task[Long] =
          proxy(Save, (id, event))
      }

    }
}

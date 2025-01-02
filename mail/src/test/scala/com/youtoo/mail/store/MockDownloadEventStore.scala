package com.youtoo
package mail
package store

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.mail.model.*

import zio.mock.*

import zio.*
import zio.prelude.*

object MockDownloadEventStore extends Mock[DownloadEventStore] {

  object ReadEvents {
    object Full extends Effect[Key, Throwable, Option[NonEmptyList[Change[DownloadEvent]]]]
    object Snapshot extends Effect[(Key, Version), Throwable, Option[NonEmptyList[Change[DownloadEvent]]]]

    object FullArgs
        extends Effect[
          (PersistenceQuery, FetchOptions),
          Throwable,
          Option[
            NonEmptyList[Change[DownloadEvent]],
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
            NonEmptyList[Change[DownloadEvent]],
          ],
        ]
  }

  object Save extends Effect[(Key, Change[DownloadEvent]), Throwable, Long]

  val compose: URLayer[Proxy, DownloadEventStore] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new DownloadEventStore {
        def readEvents(id: Key): Task[Option[NonEmptyList[Change[DownloadEvent]]]] =
          proxy(ReadEvents.Full, id)

        def readEvents(id: Key, snapshotVersion: Version): Task[Option[NonEmptyList[Change[DownloadEvent]]]] =
          proxy(ReadEvents.Snapshot, (id, snapshotVersion))

        def readEvents(
          query: PersistenceQuery,
          options: FetchOptions,
        ): Task[Option[NonEmptyList[Change[DownloadEvent]]]] =
          proxy(ReadEvents.FullArgs, (query, options))

        def readEvents(
          id: Key,
          query: PersistenceQuery,
          options: FetchOptions,
        ): Task[Option[NonEmptyList[Change[DownloadEvent]]]] =
          proxy(ReadEvents.FullArgsByAggregate, (id, query, options))

        def save(id: Key, event: Change[DownloadEvent]): Task[Long] =
          proxy(Save, (id, event))
      }

    }
}

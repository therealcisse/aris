package com.youtoo
package ingestion
package store

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.ingestion.model.*

import zio.mock.*

import zio.*
import zio.prelude.*

object MockIngestionEventStore extends Mock[IngestionEventStore] {

  object ReadEvents {
    object Full extends Effect[Key, Throwable, Option[NonEmptyList[Change[IngestionEvent]]]]
    object Snapshot extends Effect[(Key, Version), Throwable, Option[NonEmptyList[Change[IngestionEvent]]]]

    object FullArgs
        extends Effect[
          (Option[NonEmptyList[Namespace]], Option[Hierarchy], Option[NonEmptyList[EventProperty]]),
          Throwable,
          Option[
            NonEmptyList[Change[IngestionEvent]],
          ],
        ]
    object SnapshotArgs
        extends Effect[
          (Version, Option[NonEmptyList[Namespace]], Option[Hierarchy], Option[NonEmptyList[EventProperty]]),
          Throwable,
          Option[NonEmptyList[Change[IngestionEvent]]],
        ]
  }

  object Save extends Effect[(Key, Change[IngestionEvent]), Throwable, Long]

  val compose: URLayer[Proxy, IngestionEventStore] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new IngestionEventStore {
        def readEvents(id: Key): Task[Option[NonEmptyList[Change[IngestionEvent]]]] =
          proxy(ReadEvents.Full, id)

        def readEvents(id: Key, snapshotVersion: Version): Task[Option[NonEmptyList[Change[IngestionEvent]]]] =
          proxy(ReadEvents.Snapshot, (id, snapshotVersion))

        def readEvents(
          snapshotVersion: Version,
          ns: Option[NonEmptyList[Namespace]],
          hierarchy: Option[Hierarchy],
          props: Option[NonEmptyList[EventProperty]],
        ): Task[Option[NonEmptyList[Change[IngestionEvent]]]] =
          proxy(ReadEvents.SnapshotArgs, (snapshotVersion, ns, hierarchy, props))

        def readEvents(
          ns: Option[NonEmptyList[Namespace]],
          hierarchy: Option[Hierarchy],
          props: Option[NonEmptyList[EventProperty]],
        ): Task[Option[NonEmptyList[Change[IngestionEvent]]]] =
          proxy(ReadEvents.FullArgs, (ns, hierarchy, props))

        def save(id: Key, event: Change[IngestionEvent]): Task[Long] =
          proxy(Save, (id, event))
      }

    }
}

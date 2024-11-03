package com.youtoo
package ingestion
package store

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.ingestion.model.*

import zio.mock.*

import zio.*
import zio.prelude.*

object MockFileEventStore extends Mock[FileEventStore] {
  object ReadEventsById extends Effect[Key, Throwable, Option[NonEmptyList[Change[FileEvent]]]]
  object ReadEventsByIdAndVersion extends Effect[(Key, Version), Throwable, Option[NonEmptyList[Change[FileEvent]]]]
  object ReadEventsByFilters
      extends Effect[
        (Option[NonEmptyList[Namespace]], Option[Hierarchy], Option[NonEmptyList[EventProperty]]),
        Throwable,
        Option[NonEmptyList[Change[FileEvent]]],
      ]
  object ReadEventsByVersionAndFilters
      extends Effect[
        (Version, Option[NonEmptyList[Namespace]], Option[Hierarchy], Option[NonEmptyList[EventProperty]]),
        Throwable,
        Option[NonEmptyList[Change[FileEvent]]],
      ]
  object Save extends Effect[(Key, Change[FileEvent]), Throwable, Long]

  val compose: URLayer[Proxy, FileEventStore] = ZLayer.fromFunction { (proxy: Proxy) =>
    new FileEventStore {
      def readEvents(id: Key): Task[Option[NonEmptyList[Change[FileEvent]]]] = proxy(ReadEventsById, id)
      def readEvents(id: Key, snapshotVersion: Version): Task[Option[NonEmptyList[Change[FileEvent]]]] =
        proxy(ReadEventsByIdAndVersion, (id, snapshotVersion))
      def readEvents(
        ns: Option[NonEmptyList[Namespace]],
        hierarchy: Option[Hierarchy],
        props: Option[NonEmptyList[EventProperty]],
      ): Task[Option[NonEmptyList[Change[FileEvent]]]] = proxy(ReadEventsByFilters, (ns, hierarchy, props))
      def readEvents(
        snapshotVersion: Version,
        ns: Option[NonEmptyList[Namespace]],
        hierarchy: Option[Hierarchy],
        props: Option[NonEmptyList[EventProperty]],
      ): Task[Option[NonEmptyList[Change[FileEvent]]]] =
        proxy(ReadEventsByVersionAndFilters, (snapshotVersion, ns, hierarchy, props))
      def save(id: Key, event: Change[FileEvent]): Task[Long] = proxy(Save, (id, event))
    }
  }
}

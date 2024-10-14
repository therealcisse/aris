package com.youtoo.cqrs
package store

import com.youtoo.cqrs.domain.*

import zio.mock.*

import zio.*
import zio.jdbc.*
import zio.prelude.*

object MockEventStore extends Mock[EventStore[?]] {
  object ReadEventsFully extends Effect[Key, Throwable, Option[NonEmptyList[Change[?]]]]
  object ReadEventsSnapshot extends Effect[(Key, Version), Throwable, Option[NonEmptyList[Change[?]]]]
  object Save extends Effect[(Key, Change[?]), Throwable, Long]

  val compose: URLayer[Proxy, EventStore[?]] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new EventStore[?] {
        def readEvents(id: Key): Task[Option[NonEmptyList[Change[?]]]] =
          proxy(ReadEventsFully, id)

        def readEvents(id: Key, snapshotVersion: Version): Task[Option[NonEmptyList[Change[?]]]] =
          proxy(ReadEventsSnapshot, id, snapshotVersion)

        def save(id: Key, event: Change[?]): Task[Long] =
          proxy(Save, id, event)
      }
    }
}

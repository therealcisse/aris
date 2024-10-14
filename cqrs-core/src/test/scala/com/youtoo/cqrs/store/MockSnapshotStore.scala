package com.youtoo.cqrs
package store

import com.youtoo.cqrs.domain.*

import zio.mock.*

import zio.*
import zio.jdbc.*
import zio.prelude.*

object MockSnapshotStore extends Mock[SnapshotStore] {
  object ReadSnapshot extends Effect[Key, Throwable, Option[Version]]
  object SaveSnapshot extends Effect[(Key, Version), Throwable, Long]

  val compose: URLayer[Proxy, SnapshotStore] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new SnapshotStore {
        def readSnapshot(id: Key): Task[Option[Version]] =
          proxy(ReadSnapshot, id)

        def saveSnapshot(id: Key, version: Version): Task[Long] =
          proxy(SaveSnapshot, id, version)
      }
    }
}

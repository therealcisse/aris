package com.youtoo
package cqrs
package store

import zio.mock.*
import zio.jdbc.*

import zio.*

object MockSnapshotStore extends Mock[SnapshotStore] {
  object ReadSnapshot extends Effect[Key, Throwable, Option[Version]]
  object SaveSnapshot extends Effect[(Key, Version), Throwable, Long]

  val compose: URLayer[Proxy, SnapshotStore] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new SnapshotStore {
        def readSnapshot(id: Key): RIO[ZConnection, Option[Version]] =
          proxy(ReadSnapshot, id)

        def save(id: Key, version: Version): RIO[ZConnection, Long] =
          proxy(SaveSnapshot, (id, version))
      }
    }
}

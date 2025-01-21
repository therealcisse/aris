package com.youtoo
package aris
package store

import zio.mock.*

import zio.*

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

        def save(id: Key, version: Version): Task[Long] =
          proxy(SaveSnapshot, (id, version))
      }
    }
}

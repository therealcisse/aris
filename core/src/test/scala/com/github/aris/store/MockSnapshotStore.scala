package com.github
package aris
package store

import zio.mock.*

import zio.*

object MockSnapshotStore extends Mock[SnapshotStore] {
  object ReadSnapshot extends Effect[Key, Throwable, Option[Version]]
  object SaveSnapshot extends Effect[(Key, Version), Throwable, Int]

  val compose: URLayer[Proxy, SnapshotStore] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new SnapshotStore {
        def readSnapshot(id: Key): Task[Option[Version]] =
          proxy(ReadSnapshot, id)

        def save(id: Key, version: Version): Task[Int] =
          proxy(SaveSnapshot, (id, version))
      }
    }
}

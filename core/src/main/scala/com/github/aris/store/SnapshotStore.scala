package com.github
package aris
package store

import com.github.aris.service.*

import zio.*

trait SnapshotStore {
  def readSnapshot(id: Key): Task[Option[Version]]
  def save(id: Key, version: Version): Task[Int]
}

object SnapshotStore {

  def live(): ZLayer[CQRSPersistence, Throwable, SnapshotStore] =
    ZLayer.fromFunction { (persistence: CQRSPersistence) =>
      new SnapshotStore {

        def readSnapshot(id: Key): Task[Option[Version]] =
          persistence.readSnapshot(id)

        def save(id: Key, version: Version): Task[Int] =
          persistence.saveSnapshot(id, version)

      }
    }
}

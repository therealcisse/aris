package com.youtoo
package aris
package store

import com.youtoo.aris.service.*

import zio.*

trait SnapshotStore {
  def readSnapshot(id: Key): Task[Option[Version]]
  def save(id: Key, version: Version): Task[Long]
}

object SnapshotStore {

  def live(): ZLayer[CQRSPersistence, Throwable, SnapshotStore] =
    ZLayer.fromFunction { (persistence: CQRSPersistence) =>
      new SnapshotStore {

        def readSnapshot(id: Key): Task[Option[Version]] =
          persistence.readSnapshot(id)

        def save(id: Key, version: Version): Task[Long] =
          persistence.saveSnapshot(id, version)

      }
    }
}

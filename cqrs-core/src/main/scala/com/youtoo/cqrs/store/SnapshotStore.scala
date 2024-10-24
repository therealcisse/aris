package com.youtoo
package cqrs
package store

import com.youtoo.cqrs.service.*

import zio.*
import zio.jdbc.*

trait SnapshotStore {
  def readSnapshot(id: Key): RIO[ZConnection, Option[Version]]
  def save(id: Key, version: Version): RIO[ZConnection, Long]
}

object SnapshotStore {

  def live(): ZLayer[CQRSPersistence, Throwable, SnapshotStore] =
    ZLayer.fromFunction { (persistence: CQRSPersistence) =>
      new SnapshotStore {

        def readSnapshot(id: Key): RIO[ZConnection, Option[Version]] =
          persistence.readSnapshot(id)

        def save(id: Key, version: Version): RIO[ZConnection, Long] =
          persistence.saveSnapshot(id, version)

      }
    }
}

package com.youtoo.cqrs
package store

import zio.*

transparent trait SnapshotStore {
  def readSnapshot(id: Key): Task[Option[Version]]
  def save(id: Key, version: Version): Task[Unit]
}

package com.github
package aris
package store

import com.github.aris.domain.*

import zio.*
import zio.prelude.*

transparent trait EventStore[Event] {
  def readEvents(id: Key): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(id: Key, snapshotVersion: Version): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(options: FetchOptions): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(id: Key, options: FetchOptions): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(id: Key, interval: TimeInterval): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(interval: TimeInterval): Task[Option[NonEmptyList[Change[Event]]]]

  def save(id: Key, event: Change[Event]): Task[Long]
}

package com.github
package aris
package store

import com.github.aris.domain.*

import zio.*
import zio.prelude.*

transparent trait EventStore[Event] {
  def readEvents(id: Key): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(id: Key, snapshotVersion: Version): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(
    namespace: Namespace,
    options: FetchOptions,
  ): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(
    id: Key,
    namespace: Namespace,
    options: FetchOptions,
  ): Task[Option[NonEmptyList[Change[Event]]]]

  def readEvents(
    id: Key,
    namespace: Namespace,
    interval: TimeInterval,
  ): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(
    namespace: Namespace,
    interval: TimeInterval,
  ): Task[Option[NonEmptyList[Change[Event]]]]

  def save(id: Key, event: Change[Event]): Task[Long]
}

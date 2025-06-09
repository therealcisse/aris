package com.github
package aris
package store

import com.github.aris.domain.*

import zio.*
import zio.prelude.*

  transparent trait EventStore[Event] {
  def readEvents(id: Key, tag: Option[Tag]): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(id: Key, snapshotVersion: Version, tag: Option[Tag]): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(
    namespace: Namespace,
    tag: Option[Tag],
    options: FetchOptions,
  ): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(
    id: Key,
    namespace: Namespace,
    tag: Option[Tag],
    options: FetchOptions,
  ): Task[Option[NonEmptyList[Change[Event]]]]

  def readEvents(
    id: Key,
    namespace: Namespace,
    tag: Option[Tag],
    interval: TimeInterval,
  ): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(
    namespace: Namespace,
    tag: Option[Tag],
    interval: TimeInterval,
  ): Task[Option[NonEmptyList[Change[Event]]]]

  def save(id: Key, event: Change[Event]): Task[Long]
}

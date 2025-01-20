package com.youtoo
package aris
package store

import com.youtoo.aris.domain.*

import zio.*
import zio.prelude.*

transparent trait EventStore[Event] {
  def readEvents(id: Key): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(id: Key, snapshotVersion: Version): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(
    query: PersistenceQuery,
    options: FetchOptions,
  ): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(
    id: Key,
    query: PersistenceQuery,
    options: FetchOptions,
  ): Task[Option[NonEmptyList[Change[Event]]]]

  def readEvents(
    id: Key,
    query: PersistenceQuery,
    interval: TimeInterval,
  ): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(
    query: PersistenceQuery,
    interval: TimeInterval,
  ): Task[Option[NonEmptyList[Change[Event]]]]

  def save(id: Key, event: Change[Event]): Task[Long]
}

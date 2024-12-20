package com.youtoo
package cqrs
package store

import com.youtoo.cqrs.domain.*

import zio.*
import zio.jdbc.*
import zio.prelude.*

transparent trait EventStore[Event] {
  def readEvents(id: Key): RIO[ZConnection, Option[NonEmptyList[Change[Event]]]]
  def readEvents(id: Key, snapshotVersion: Version): RIO[ZConnection, Option[NonEmptyList[Change[Event]]]]
  def readEvents(
    query: PersistenceQuery,
    options: FetchOptions,
  ): RIO[ZConnection, Option[NonEmptyList[Change[Event]]]]
  def readEvents(
    id: Key,
    query: PersistenceQuery,
    options: FetchOptions,
  ): RIO[ZConnection, Option[NonEmptyList[Change[Event]]]]

  def save(id: Key, event: Change[Event]): RIO[ZConnection, Long]
}

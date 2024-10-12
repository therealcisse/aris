package com.youtoo.cqrs
package store

import com.youtoo.cqrs.domain.*

import zio.*
import zio.prelude.*

transparent trait EventStore[Event] {
  def readEvents(id: Key): Task[Option[NonEmptyList[Change[Event]]]]
  def save(id: Key, event: Change[Event]): Task[Unit]
}

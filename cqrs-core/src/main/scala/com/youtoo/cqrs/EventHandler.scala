package com.youtoo
package cqrs

import com.youtoo.cqrs.domain.*

import zio.prelude.*

trait EventHandler[Event, T] {
  def applyEvents(events: NonEmptyList[Change[Event]]): T
  def applyEvents(zero: T, events: NonEmptyList[Change[Event]]): T
}

object EventHandler {
  inline def applyEvents[Event, T](events: NonEmptyList[Change[Event]])(using h: EventHandler[Event, T]): T =
    h.applyEvents(events)
  inline def applyEvents[Event, T](zero: T, events: NonEmptyList[Change[Event]])(using h: EventHandler[Event, T]): T =
    h.applyEvents(zero, events)

}

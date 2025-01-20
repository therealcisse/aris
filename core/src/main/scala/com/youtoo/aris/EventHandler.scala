package com.youtoo
package aris

import com.youtoo.aris.domain.*

import zio.prelude.*

trait EventHandler[Event, T] {
  def applyEvents(events: NonEmptyList[Change[Event]]): Option[T]
  def applyEvents(zero: T, events: NonEmptyList[Change[Event]]): Option[T]
}

object EventHandler {
  inline def applyEvents[Event, T](events: NonEmptyList[Change[Event]])(using h: EventHandler[Event, T]): Option[T] =
    h.applyEvents(events)
  inline def applyEvents[Event, T](zero: T, events: NonEmptyList[Change[Event]])(using
    h: EventHandler[Event, T],
  ): Option[T] =
    h.applyEvents(zero, events)

}

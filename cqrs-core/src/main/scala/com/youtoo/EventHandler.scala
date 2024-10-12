package com.youtoo.cqrs

import com.youtoo.cqrs.domain.*

import zio.prelude.*

trait EventHandler[Event, T] {
  def applyEvents(events: NonEmptyList[Event]): T
}

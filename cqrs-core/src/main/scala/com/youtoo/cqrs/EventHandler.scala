package com.youtoo
package cqrs

import com.youtoo.cqrs.domain.*

import zio.prelude.*

trait EventHandler[Event, T] {
  def applyEvents(events: NonEmptyList[Change[Event]]): T
  def applyEvents(zero: T, events: NonEmptyList[Change[Event]]): T
}

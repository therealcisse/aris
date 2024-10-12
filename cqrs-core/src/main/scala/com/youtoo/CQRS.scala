package com.youtoo.cqrs

import com.youtoo.cqrs.domain.*

import zio.*

transparent trait CQRS[Command, Event, T] {
  def add(id: Key, cmd: Command): Task[Unit]
  def load(id: Key): Task[Option[T]]
}

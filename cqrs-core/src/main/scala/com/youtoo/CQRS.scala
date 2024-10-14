package com.youtoo.cqrs

import com.youtoo.cqrs.domain.*

import zio.*
import zio.jdbc.*
import com.youtoo.cqrs.service.*

transparent trait CQRS[Command, Event, T] {
  def add(id: Key, cmd: Command)(using CmdHandler[Command, Event]): RIO[ZConnectionPool & CQRSPersistence, Unit]
  def load(id: Key)(using EventHandler[Event, T]): RIO[ZConnectionPool & CQRSPersistence, Option[T]]
}

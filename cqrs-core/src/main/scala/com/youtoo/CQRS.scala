package com.youtoo.cqrs

import zio.*

transparent trait CQRS[T, Event: [X] =>> EventHandler[X, T]: MetaInfo, Command: [X] =>> CmdHandler[X, Event]] {
  def add(id: Key, cmd: Command): Task[Unit]
  def load(id: Key): Task[Option[T]]
}

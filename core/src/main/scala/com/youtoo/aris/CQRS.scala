package com.youtoo
package aris

import zio.*

transparent trait CQRS[Event, Command: [X] =>> CmdHandler[X, Event]] {
  def add(id: Key, cmd: Command): Task[Unit]
}

object CQRS {
  transparent trait Loader[T] {
    def load(id: Key): Task[Option[T]]
  }

}

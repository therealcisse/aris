package com.youtoo
package cqrs

import zio.*

transparent trait CQRS[T, Event, Command: [X] =>> CmdHandler[X, Event]] {
  def add(id: Key, cmd: Command): Task[Unit]
}

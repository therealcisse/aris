package com.youtoo
package cqrs

import zio.*

transparent trait CQRS[Event, Command: [X] =>> CmdHandler[X, Event]] {
  def add(id: Key, cmd: Command): Task[Unit]
}

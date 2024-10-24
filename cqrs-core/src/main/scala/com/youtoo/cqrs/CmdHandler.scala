package com.youtoo
package cqrs

import zio.prelude.*

trait CmdHandler[Command, Event] {
  def applyCmd(cmd: Command): NonEmptyList[Event]
}

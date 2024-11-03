package com.youtoo
package cqrs

import zio.prelude.*

trait CmdHandler[Command, Event] {
  def applyCmd(cmd: Command): NonEmptyList[Event]
}

object CmdHandler {
  inline def applyCmd[Command, Event](cmd: Command)(using h: CmdHandler[Command, Event]): NonEmptyList[Event] =
    h.applyCmd(cmd)

}

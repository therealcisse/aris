package com.youtoo
package sink
package model

import zio.schema.{DeriveSchema, Schema}
import zio.prelude.*

import com.youtoo.cqrs.*

enum SinkCommand {
  case AddOrModify(id: SinkDefinition.Id, info: SinkType)
  case Delete(id: SinkDefinition.Id)
}

object SinkCommand {
  given Schema[SinkCommand] = DeriveSchema.gen

  given CmdHandler[SinkCommand, SinkEvent] {
    def applyCmd(cmd: SinkCommand): NonEmptyList[SinkEvent] =
      cmd match {
        case SinkCommand.AddOrModify(id, info) =>
          NonEmptyList(SinkEvent.Added(id, info))
        case SinkCommand.Delete(id) =>
          NonEmptyList(SinkEvent.Deleted(id))
      }
  }
}

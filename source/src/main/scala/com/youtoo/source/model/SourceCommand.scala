package com.youtoo
package source
package model

import zio.schema.{DeriveSchema, Schema}
import zio.prelude.*

import com.youtoo.cqrs.*

enum SourceCommand {
  case AddOrModify(id: SourceDefinition.Id, info: SourceType)
  case Delete(id: SourceDefinition.Id)
}

object SourceCommand {
  given Schema[SourceCommand] = DeriveSchema.gen

  given CmdHandler[SourceCommand, SourceEvent] {
    def applyCmd(cmd: SourceCommand): NonEmptyList[SourceEvent] =
      cmd match {
        case SourceCommand.AddOrModify(id, info) =>
          NonEmptyList(SourceEvent.Added(id, info))
        case SourceCommand.Delete(id) =>
          NonEmptyList(SourceEvent.Deleted(id))
      }
  }
}

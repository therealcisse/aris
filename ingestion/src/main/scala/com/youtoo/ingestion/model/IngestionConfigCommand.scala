package com.youtoo
package ingestion
package model

import zio.schema.{DeriveSchema, Schema}
import zio.prelude.*

import com.youtoo.cqrs.*

import com.youtoo.sink.model.*
import com.youtoo.source.model.*

enum IngestionConfigCommand {
  case AddConnection(id: IngestionConfig.Connection.Id, source: SourceDefinition.Id, sinks: NonEmptySet[SinkDefinition.Id])
}

object IngestionConfigCommand {
  given Schema[IngestionConfigCommand] = DeriveSchema.gen

  given CmdHandler[IngestionConfigCommand, IngestionConfigEvent] {
    def applyCmd(cmd: IngestionConfigCommand): NonEmptyList[IngestionConfigEvent] =
      cmd match {
        case IngestionConfigCommand.AddConnection(id, source, sinks) =>
          NonEmptyList(IngestionConfigEvent.ConnectionAdded(IngestionConfig.Connection(id, source, sinks)))
      }
  }
}

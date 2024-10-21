package com.youtoo.cqrs
package migration
package model

import zio.*

import zio.prelude.*
import zio.schema.*

enum MigrationCommand {
  case RegisterMigration(id: Migration.Id, timestamp: Timestamp)
  case StartExecution(id: Execution.Id, timestamp: Timestamp)
  case StartProcessingKey(id: Execution.Id, key: Key)
  case ProcessKey(id: Execution.Id, key: Key)
  case FailKey(id: Execution.Id, key: Key)
  case StopExecution(id: Execution.Id, timestamp: Timestamp)
  case FinishExecution(id: Execution.Id, timestamp: Timestamp)

}

type MigrationCommandHandler = CmdHandler[MigrationCommand, MigrationEvent]

object MigrationCommand {
  given Schema[MigrationCommand] = DeriveSchema.gen

  given MigrationCommandHandler with {
    def applyCmd(cmd: MigrationCommand): NonEmptyList[MigrationEvent] =
      cmd match {
        case MigrationCommand.RegisterMigration(id, timestamp) =>
          NonEmptyList(MigrationEvent.MigrationRegistered(id = id, timestamp = timestamp))

        case MigrationCommand.StartExecution(id, timestamp) =>
          NonEmptyList(MigrationEvent.ExecutionStarted(id = id, timestamp = timestamp))

        case MigrationCommand.StartProcessingKey(id, key) =>
          NonEmptyList(MigrationEvent.ProcessingStarted(id = id, key = key))

        case MigrationCommand.ProcessKey(id, key) =>
          NonEmptyList(MigrationEvent.KeyProcessed(id = id, key = key))

        case MigrationCommand.FailKey(id, key) =>
          NonEmptyList(MigrationEvent.ProcessingFailed(id = id, key = key))

        case MigrationCommand.StopExecution(id, timestamp) =>
          NonEmptyList(MigrationEvent.ExecutionStopped(id = id, timestamp = timestamp))

        case MigrationCommand.FinishExecution(id, timestamp) =>
          NonEmptyList(MigrationEvent.ExecutionFinished(id = id, timestamp = timestamp))

      }
  }

}

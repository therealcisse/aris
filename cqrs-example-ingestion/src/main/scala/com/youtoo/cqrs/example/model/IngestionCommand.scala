package com.youtoo.cqrs
package example
package model

import zio.*

import zio.prelude.*
import zio.schema.*

enum IngestionCommand {
  case StartIngestion(id: Ingestion.Id, timestamp: Timestamp)
  case SetFiles(files: Set[String])
  case FileProcessed(file: String)
  case FileFailed(file: String)
  case FinishIngestion()

}

type IngestionCommandHandler = CmdHandler[IngestionCommand, IngestionEvent]

object IngestionCommand {
  given Schema[IngestionCommand] = DeriveSchema.gen

  given IngestionCommandHandler with {
    def applyCmd(cmd: IngestionCommand): NonEmptyList[IngestionEvent] =
      cmd match {
        case IngestionCommand.StartIngestion(id, timestamp) =>
          NonEmptyList(IngestionEvent.IngestionStarted(id = id, timestamp = timestamp))

        case IngestionCommand.SetFiles(files) =>
          NonEmptyList(IngestionEvent.IngestionFilesResolved(files))

        case IngestionCommand.FileProcessed(file) =>
          NonEmptyList(IngestionEvent.IngestionFileProcessed(file))

        case IngestionCommand.FileFailed(file) =>
          NonEmptyList(IngestionEvent.IngestionFileFailed(file))

        case IngestionCommand.FinishIngestion() =>
          NonEmptyList(IngestionEvent.IngestionCompleted())

      }
  }

}

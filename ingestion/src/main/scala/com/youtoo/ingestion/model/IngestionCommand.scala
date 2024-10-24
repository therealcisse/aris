package com.youtoo.cqrs
package ingestion
package model

import zio.*

import zio.prelude.*
import zio.schema.*

enum IngestionCommand {
  case StartIngestion(id: Ingestion.Id, timestamp: Timestamp)
  case SetFiles(files: NonEmptySet[String])
  case FileProcessing(file: String)
  case FileProcessed(file: String)
  case FileFailed(file: String)
  case StopIngestion(timestamp: Timestamp)

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

        case IngestionCommand.FileProcessing(file) =>
          NonEmptyList(IngestionEvent.IngestionFileProcessing(file))

        case IngestionCommand.FileProcessed(file) =>
          NonEmptyList(IngestionEvent.IngestionFileProcessed(file))

        case IngestionCommand.FileFailed(file) =>
          NonEmptyList(IngestionEvent.IngestionFileFailed(file))

        case IngestionCommand.StopIngestion(timestamp) =>
          NonEmptyList(IngestionEvent.IngestionCompleted(timestamp))

      }
  }

}

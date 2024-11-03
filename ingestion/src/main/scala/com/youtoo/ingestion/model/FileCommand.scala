package com.youtoo
package ingestion
package model

import zio.*

import com.youtoo.cqrs.*

import zio.prelude.*
import zio.schema.*

enum FileCommand {
  case AddFile(
    provider: Provider.Id,
    id: IngestionFile.Id,
    name: IngestionFile.Name,
    metadata: IngestionFile.Metadata,
    sig: IngestionFile.Sig,
  )

  case AddProvider(
    id: Provider.Id,
    name: Provider.Name,
    location: Provider.Location,
  )

}

type FileCommandHandler = CmdHandler[FileCommand, FileEvent]

object FileCommand {
  given Schema[FileCommand] = DeriveSchema.gen

  given FileCommandHandler with {
    def applyCmd(cmd: FileCommand): NonEmptyList[FileEvent] =
      cmd match {
        case FileCommand.AddFile(provider, id, name, metadata, sig) =>
          NonEmptyList(FileEvent.FileAdded(provider, id, name, metadata, sig))

        case FileCommand.AddProvider(id, name, location) =>
          NonEmptyList(FileEvent.ProviderAdded(id, name, location))

      }

  }

}

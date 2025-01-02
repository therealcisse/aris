package com.youtoo
package mail
package model

import com.youtoo.job.model.*
import com.youtoo.cqrs.*
import zio.prelude.*

enum DownloadCommand {
  case RecordDownload(version: Version, jobId: Job.Id)
}

object DownloadCommand {
  import zio.schema.*

  given Schema[DownloadCommand] = DeriveSchema.gen

  given CmdHandler[DownloadCommand, DownloadEvent] {
    def applyCmd(cmd: DownloadCommand): NonEmptyList[DownloadEvent] =
      cmd match {
        case DownloadCommand.RecordDownload(version, jobId) =>
          NonEmptyList(DownloadEvent.Downloaded(version, jobId))
      }
  }
}


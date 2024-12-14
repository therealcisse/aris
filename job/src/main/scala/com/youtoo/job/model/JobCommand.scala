package com.youtoo
package job
package model

import com.youtoo.cqrs.*
import zio.schema.*

import zio.prelude.*

enum JobCommand {
  case StartJob(id: Job.Id, timestamp: Timestamp, total: JobMeasurement, tag: Job.Tag)
  case ReportProgress(id: Job.Id, timestamp: Timestamp, progress: Progress)
  case Done(id: Job.Id, timestamp: Timestamp, reason: Job.CompletionReason)
}

type JobCommandHandler = CmdHandler[JobCommand, JobEvent]

object JobCommand {
  given Schema[JobCommand] = DeriveSchema.gen

  given JobCommandHandler {
    def applyCmd(cmd: JobCommand): NonEmptyList[JobEvent] =
      cmd match {
        case JobCommand.StartJob(id, timestamp, total, tag) =>
          NonEmptyList(JobEvent.JobStarted(id, timestamp, total, tag))
        case JobCommand.ReportProgress(id, timestamp, progress) =>
          NonEmptyList(JobEvent.ProgressReported(id, timestamp, progress))
        case JobCommand.Done(id, timestamp, reason) =>
          NonEmptyList(JobEvent.JobCompleted(id, timestamp, reason))
      }
  }
}


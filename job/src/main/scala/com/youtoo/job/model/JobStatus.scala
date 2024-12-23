package com.youtoo
package job
package model

import zio.schema.*

enum JobStatus {
  case Running(started: Timestamp, lastUpdated: Timestamp, progress: Progress)
  case Completed(execution: JobStatus.Running, timestamp: Timestamp, reason: Job.CompletionReason)
}

object JobStatus {
  given Schema[JobStatus] = DeriveSchema.gen

  extension (status: JobStatus)
    def isCompleted: Boolean = status match {
      case _: JobStatus.Completed => true
      case _ => false
    }

  extension (status: JobStatus)
    def isCancelled: Boolean = status match {
      case s: JobStatus.Completed => s.reason.isCancellation()
      case _ => false
    }
}

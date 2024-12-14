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

}

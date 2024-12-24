package com.youtoo
package job
package service

import com.youtoo.job.model.*
import zio.mock.*
import zio.*

object JobServiceMock extends Mock[JobService] {

  object IsCancelled extends Effect[Job.Id, Throwable, Boolean]
  object Load extends Effect[Job.Id, Throwable, Option[Job]]
  object LoadMany extends Effect[(Option[Key], Long), Throwable, Chunk[Job]]
  object Save extends Effect[Job, Throwable, Long]
  object StartJob extends Effect[(Job.Id, Timestamp, JobMeasurement, Job.Tag), Throwable, Unit]
  object ReportProgress extends Effect[(Job.Id, Timestamp, Progress), Throwable, Unit]
  object CancelJob extends Effect[(Job.Id, Timestamp), Throwable, Unit]
  object CompleteJob extends Effect[(Job.Id, Timestamp, Job.CompletionReason), Throwable, Unit]

  val compose: URLayer[Proxy, JobService] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new JobService {
        def isCancelled(id: Job.Id): Task[Boolean] =
          proxy(IsCancelled, id)

        def load(id: Job.Id): Task[Option[Job]] =
          proxy(Load, id)

        def loadMany(offset: Option[Key], limit: Long): Task[Chunk[Job]] =
          proxy(LoadMany, (offset, limit))

        def save(job: Job): Task[Long] =
          proxy(Save, job)

        def startJob(id: Job.Id, timestamp: Timestamp, total: JobMeasurement, tag: Job.Tag): Task[Unit] =
          proxy(StartJob, (id, timestamp, total, tag))

        def reportProgress(id: Job.Id, timestamp: Timestamp, progress: Progress): Task[Unit] =
          proxy(ReportProgress, (id, timestamp, progress))

        def completeJob(id: Job.Id, timestamp: Timestamp, reason: Job.CompletionReason): Task[Unit] =
          proxy(CompleteJob, (id, timestamp, reason))

        def cancelJob(id: Job.Id, timestamp: Timestamp): Task[Unit] =
          proxy(CancelJob, (id, timestamp))
      }
    }
}

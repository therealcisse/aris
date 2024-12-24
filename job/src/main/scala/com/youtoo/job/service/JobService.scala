package com.youtoo
package job
package service

import cats.implicits.*

import zio.*
import zio.jdbc.*
import com.youtoo.postgres.*
import com.youtoo.job.store.*
import com.youtoo.job.model.*
import com.youtoo.job.repository.*
import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.*

import zio.telemetry.opentelemetry.tracing.*
import zio.telemetry.opentelemetry.common.*

trait JobService {
  def isCancelled(id: Job.Id): Task[Boolean]

  def load(id: Job.Id): Task[Option[Job]]
  def loadMany(offset: Option[Key], limit: Long): Task[Chunk[Job]]
  def save(job: Job): Task[Long]

  def cancelJob(id: Job.Id, timestamp: Timestamp): Task[Unit]
  def startJob(id: Job.Id, timestamp: Timestamp, total: JobMeasurement, tag: Job.Tag): Task[Unit]
  def reportProgress(id: Job.Id, timestamp: Timestamp, progress: Progress): Task[Unit]
  def completeJob(id: Job.Id, timestamp: Timestamp, reason: Job.CompletionReason): Task[Unit]
}

object JobService {

  inline def loadMany(offset: Option[Key], limit: Long): RIO[JobService, Chunk[Job]] =
    ZIO.serviceWithZIO(_.loadMany(offset, limit))

  inline def load(id: Job.Id): RIO[JobService, Option[Job]] =
    ZIO.serviceWithZIO(_.load(id))

  inline def save(job: Job): RIO[JobService, Long] =
    ZIO.serviceWithZIO(_.save(job))

  inline def startJob(id: Job.Id, timestamp: Timestamp, total: JobMeasurement, tag: Job.Tag): RIO[JobService, Unit] =
    ZIO.serviceWithZIO(_.startJob(id, timestamp, total, tag))

  inline def reportProgress(id: Job.Id, timestamp: Timestamp, progress: Progress): RIO[JobService, Unit] =
    ZIO.serviceWithZIO(_.reportProgress(id, timestamp, progress))

  inline def completeJob(id: Job.Id, timestamp: Timestamp, reason: Job.CompletionReason): RIO[JobService, Unit] =
    ZIO.serviceWithZIO(_.completeJob(id, timestamp, reason))

  inline def cancelJob(id: Job.Id, timestamp: Timestamp): RIO[JobService, Unit] =
    ZIO.serviceWithZIO(_.cancelJob(id, timestamp))

  inline def isCancelled(id: Job.Id): RIO[JobService, Boolean] =
    ZIO.serviceWithZIO(_.isCancelled(id))

  def live(): ZLayer[
    ZConnectionPool & JobEventStore & JobRepository & SnapshotStore & SnapshotStrategy.Factory & Tracing & JobCQRS,
    Throwable,
    JobService,
  ] =
    ZLayer.fromFunction {
      (
        repository: JobRepository,
        pool: ZConnectionPool,
        snapshotStore: SnapshotStore,
        eventStore: JobEventStore,
        factory: SnapshotStrategy.Factory,
        tracing: Tracing,
        jobCQRS: JobCQRS,
      ) =>
        ZLayer {
          factory.create(JobEvent.discriminator) map { strategy =>
            new JobServiceLive(repository, pool, snapshotStore, eventStore, strategy, jobCQRS).traced(tracing)
          }
        }
    }.flatten

  class JobServiceLive(
    repository: JobRepository,
    pool: ZConnectionPool,
    snapshotStore: SnapshotStore,
    eventStore: JobEventStore,
    strategy: SnapshotStrategy,
    jobCQRS: JobCQRS,
  ) extends JobService { self =>
    def isCancelled(id: Job.Id): Task[Boolean] =
      val key = id.asKey

      atomically {
        val events = eventStore.readEvents(
          id = key,
          PersistenceQuery.ns(JobEvent.NS.JobCompleted),
          FetchOptions.limit(1L),
        )

        events map (_.fold(false) { es =>
          EventHandler.applyEvents(es)(using JobEvent.LoadIsCancelled())
        })

      }.provideEnvironment(ZEnvironment(pool))

    def load(id: Job.Id): Task[Option[Job]] =
      val key = id.asKey

      atomically {
        val deps = (
          repository.load(id) <&> snapshotStore.readSnapshot(key)
        ).map(_.tupled)

        val o = deps flatMap {
          case None =>
            for {
              events <- eventStore.readEvents(key)
              inn = events map { es =>
                (
                  EventHandler.applyEvents(es),
                  es.toList.maxBy(_.version),
                  es.size,
                  None,
                )
              }

            } yield inn

          case Some((in, version)) =>
            val events = eventStore.readEvents(key, snapshotVersion = version)

            events map (_.map { es =>
              (
                EventHandler.applyEvents(in, es),
                es.toList.maxBy(_.version),
                es.size,
                version.some,
              )
            })

        }

        o flatMap (_.fold(ZIO.none) {
          case (inn, ch, size, version) if strategy(version, size) =>
            (repository.save(inn) <&> snapshotStore.save(id = key, version = ch.version)) `as` inn.some

          case (inn, _, _, _) => ZIO.some(inn)
        })

      }.provideEnvironment(ZEnvironment(pool))

    def loadMany(offset: Option[Key], limit: Long): Task[Chunk[Job]] =
      atomically(repository.loadMany(offset, limit)).provideEnvironment(ZEnvironment(pool))

    def save(job: Job): Task[Long] =
      atomically(repository.save(job)).provideEnvironment(ZEnvironment(pool))

    def startJob(id: Job.Id, timestamp: Timestamp, total: JobMeasurement, tag: Job.Tag): Task[Unit] =
      jobCQRS.add(id.asKey, JobCommand.StartJob(id, timestamp, total, tag))

    def reportProgress(id: Job.Id, timestamp: Timestamp, progress: Progress): Task[Unit] =
      jobCQRS
        .add(id.asKey, JobCommand.ReportProgress(id, timestamp, progress))

    def completeJob(id: Job.Id, timestamp: Timestamp, reason: Job.CompletionReason): Task[Unit] =
      jobCQRS.add(id.asKey, JobCommand.CompleteJob(id, timestamp, reason))

    def cancelJob(id: Job.Id, timestamp: Timestamp): Task[Unit] =
      jobCQRS.add(id.asKey, JobCommand.CancelJob(id, timestamp))

    def traced(tracing: Tracing): JobService = new JobService {
      def isCancelled(id: Job.Id): Task[Boolean] =
        self.isCancelled(id) @@ tracing.aspects.span(
          "JobService.isCancelled",
          attributes = Attributes(Attribute.long("jobId", id.asKey.value)),
        )

      def load(id: Job.Id): Task[Option[Job]] =
        self.load(id) @@ tracing.aspects.span(
          "JobService.load",
          attributes = Attributes(Attribute.long("jobId", id.asKey.value)),
        )
      def loadMany(offset: Option[Key], limit: Long): Task[Chunk[Job]] =
        self.loadMany(offset, limit) @@ tracing.aspects.span("JobService.loadMany")
      def save(job: Job): Task[Long] =
        self.save(job) @@ tracing.aspects.span(
          "JobService.save",
          attributes = Attributes(Attribute.long("jobId", job.id.asKey.value)),
        )
      def startJob(id: Job.Id, timestamp: Timestamp, total: JobMeasurement, tag: Job.Tag): Task[Unit] =
        self.startJob(id, timestamp, total, tag) @@ tracing.aspects.span(
          "JobService.startJob",
          attributes = Attributes(Attribute.string("tag", tag.value)),
        )
      def reportProgress(id: Job.Id, timestamp: Timestamp, progress: Progress): Task[Unit] =
        self.reportProgress(id, timestamp, progress) @@ tracing.aspects.span(
          "JobService.reportProgress",
          attributes = Attributes(Attribute.long("jobId", id.asKey.value)),
        )
      def completeJob(id: Job.Id, timestamp: Timestamp, reason: Job.CompletionReason): Task[Unit] =
        self.completeJob(id, timestamp, reason) @@ tracing.aspects.span(
          "JobService.completeJob",
          attributes = Attributes(Attribute.long("jobId", id.asKey.value)),
        )

      def cancelJob(id: Job.Id, timestamp: Timestamp): Task[Unit] =
        self.cancelJob(id, timestamp) @@ tracing.aspects.span(
          "JobService.cancelJob",
          attributes = Attributes(Attribute.long("jobId", id.asKey.value)),
        )

    }
  }
}

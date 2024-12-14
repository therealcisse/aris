package com.youtoo
package job
package service

import cats.implicits.*
import zio.test.*
import zio.prelude.*
import zio.mock.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*
import zio.jdbc.*

import com.youtoo.postgres.*
import com.youtoo.cqrs.*
import com.youtoo.cqrs.service.postgres.*

import com.youtoo.job.repository.*
import com.youtoo.job.model.*
import com.youtoo.cqrs.store.*
import com.youtoo.job.store.*

object JobServiceSpec extends MockSpecDefault, TestSupport {
  inline val Threshold = 10

  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    Log.layer >>> testEnvironment ++ Runtime.setConfigProvider(
      ConfigProvider.fromMap(Map("Job.snapshots.threshold" -> s"$Threshold")),
    )

  def spec = suite("JobServiceSpec")(
    test("should load job") {
      check(
        Gen.option(jobGen),
        Gen.option(versionGen), // These generators need to be similar to those used job your context
        validEventSequenceGen,
      ) { case (job, version, events) =>
        val maxChange = events.toList.maxBy(_.version)
        val (id, deps) = (job, version).tupled match {
          // Handling case of job and version being None
          case None =>
            val sendSnapshot = events.size >= Threshold
            val job @ Job(id, _, _, _) = summon[EventHandler[JobEvent, Job]].applyEvents(events)

            val loadMock = JobRepositoryMock.Load(equalTo(id), value(None))
            val readSnapshotMock = MockSnapshotStore.ReadSnapshot(equalTo(id), value(None))
            val saveMock = JobRepositoryMock.Save(equalTo(job), value(1L))
            val saveSnapshotMock = MockSnapshotStore.SaveSnapshot(equalTo((id.asKey, maxChange.version)), value(1L))
            val readEventsMock = JobEventStoreMock.ReadEventsById(equalTo(id.asKey), value(events.some))

            val layers =
              if sendSnapshot then
                ((loadMock ++ readSnapshotMock) || (readSnapshotMock ++ loadMock)) ++ readEventsMock ++
                  ((saveMock ++ saveSnapshotMock) || (saveSnapshotMock ++ saveMock)) ++ MockJobCQRS.empty
              else
                ((loadMock ++ readSnapshotMock) || (readSnapshotMock ++ loadMock)) ++ readEventsMock ++ MockJobCQRS.empty

            (id, layers)

          case Some((job @ Job(id, _, _, _), v)) if !job.status.isCompleted =>
            val sendSnapshot = (events.size - 1) >= Threshold

            val es = NonEmptyList.fromIterableOption(events.tail)

            val inn = es match {
              case None => job
              case Some(nel) => summon[EventHandler[JobEvent, Job]].applyEvents(job, nel)
            }

            val readSnapshotMock = MockSnapshotStore.ReadSnapshot(equalTo(id.asKey), value(v.some))
            val loadMock = JobRepositoryMock.Load(equalTo(id), value(job.some))
            val saveMock = JobRepositoryMock.Save(equalTo(inn), value(1L))
            val saveSnapshotMock = MockSnapshotStore.SaveSnapshot(equalTo((id.asKey, maxChange.version)), value(1L))
            val readEventsMock = JobEventStoreMock.ReadEventsByIdAndVersion(equalTo((id.asKey, v)), value(es))

            val layers =
              if sendSnapshot then
                ((readSnapshotMock ++ loadMock) || (loadMock ++ readSnapshotMock)) ++ readEventsMock ++ ((saveMock ++ saveSnapshotMock) || (saveSnapshotMock ++ saveMock)) ++ MockJobCQRS.empty
              else
                ((readSnapshotMock ++ loadMock) || (loadMock ++ readSnapshotMock)) ++ readEventsMock ++ MockJobCQRS.empty

            (id, layers)

          case Some((job @ Job(id, _, _, _), v)) =>
            val readSnapshotMock = MockSnapshotStore.ReadSnapshot(equalTo(id.asKey), value(v.some))
            val loadMock = JobRepositoryMock.Load(equalTo(id), value(job.some))
            val readEventsMock = JobEventStoreMock.ReadEventsByIdAndVersion(equalTo((id.asKey, v)), value(None))

            val layers =
              ((readSnapshotMock ++ loadMock) || (loadMock ++ readSnapshotMock)) ++ readEventsMock ++ MockJobCQRS.empty

            (id, layers)

        }

        (for {
          _ <- JobService.load(id)
        } yield assertCompletes)
          .provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
            (deps ++ SnapshotStrategy.live()) >>> JobService.live(),
          )
      }
    },
    test("save job returns expected result using JobRepository") {
      check(jobGen) { case job =>
        val expected = 1L

        val mockEnv = JobRepositoryMock.Save(
          equalTo(job),
          value(expected),
        )

        (for {
          effect <- atomically(JobService.save(job))
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            JobRepository & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            JobService,
          ](
            JobCQRS.live(),
            PostgresCQRSPersistence.live(),
            JobEventStore.live(),
            SnapshotStore.live(),
            SnapshotStrategy.live(),
            JobService.live(),
          ),
        )
      }
    },
    test("load many jobs returns expected result using JobRepository") {
      check(Gen.option(keyGen), Gen.long, keyGen) { case (key, limit, id) =>
        val expected = Chunk(id)

        val mockEnv = JobRepositoryMock.LoadMany(
          equalTo((key, limit)),
          value(expected),
        )

        (for {
          effect <- atomically(JobService.loadMany(offset = key, limit = limit))
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            JobRepository & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            JobService,
          ](
            JobCQRS.live(),
            PostgresCQRSPersistence.live(),
            JobEventStore.live(),
            SnapshotStore.live(),
            SnapshotStrategy.live(),
            JobService.live(),
          ),
        )
      }
    },
    test("should start a job using JobCQRS") {
      check(jobIdGen, timestampGen, jobMeasurementGen, jobTagGen) { (id, timestamp, total, tag) =>
        val mockEnv = MockJobCQRS.Add(
          equalTo(
            (
              id,
              JobCommand.StartJob(id, timestamp, total, tag),
            ),
          ),
        )

        (for {
          _ <- JobService.startJob(id, timestamp, total, tag)
        } yield assertCompletes)
          .provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
            mockEnv.toLayer >>> ZLayer.makeSome[
              JobCQRS & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
              JobService,
            ](
              JobRepository.live(),
              PostgresCQRSPersistence.live(),
              JobEventStore.live(),
              SnapshotStore.live(),
              SnapshotStrategy.live(),
              JobService.live(),
            ),
          )
      }
    },
    test("should report progress of a job using JobCQRS") {
      check(jobIdGen, timestampGen, progressGen) { (id, timestamp, progress) =>
        val mockEnv = MockJobCQRS.Add(
          equalTo(
            (
              id,
              JobCommand.ReportProgress(id, timestamp, progress),
            ),
          ),
        )

        (for {
          _ <- JobService.reportProgress(id, timestamp, progress)
        } yield assertCompletes)
          .provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
            mockEnv.toLayer >>> ZLayer.makeSome[
              JobCQRS & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
              JobService,
            ](
              JobRepository.live(),
              PostgresCQRSPersistence.live(),
              JobEventStore.live(),
              SnapshotStore.live(),
              SnapshotStrategy.live(),
              JobService.live(),
            ),
          )
      }
    },
    test("should complete a job using JobCQRS") {
      check(jobIdGen, timestampGen, jobCompletionReasonGen) { (id, timestamp, reason) =>
        val mockEnv = MockJobCQRS.Add(
          equalTo(
            (
              id,
              JobCommand.CompleteJob(id, timestamp, reason),
            ),
          ),
        )

        (for {
          _ <- JobService.completeJob(id, timestamp, reason)
        } yield assertCompletes)
          .provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
            mockEnv.toLayer >>> ZLayer.makeSome[
              JobCQRS & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
              JobService,
            ](
              JobRepository.live(),
              PostgresCQRSPersistence.live(),
              JobEventStore.live(),
              SnapshotStore.live(),
              SnapshotStrategy.live(),
              JobService.live(),
            ),
          )
      }
    },
  ).provideSomeLayerShared(
    ZConnectionMock.pool() ++ (zio.telemetry.opentelemetry.OpenTelemetry.contextZIO >>> tracingMockLayer()),
  ) @@ TestAspect.withLiveClock
}

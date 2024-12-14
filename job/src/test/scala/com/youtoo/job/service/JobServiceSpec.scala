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
import com.youtoo.cqrs.service.*
import com.youtoo.cqrs.*

import com.youtoo.job.repository.*
import com.youtoo.job.model.*
import com.youtoo.cqrs.store.*
import com.youtoo.job.store.*

object JobServiceSpec extends MockSpecDefault, TestSupport {

  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    // Assuming similar logging and configuration as IngestionServiceSpec
    Log.layer >>> testEnvironment

  def spec = suite("JobServiceSpec")(
    test("should load job") {
      check(
        Gen.option(jobGen),
        Gen.option(versionGen), // These generators need to be similar to those used in your context
        validEventSequenceGen
      ) { case (job, version, events) =>
        val maxChange = events.toList.maxBy(_.version)
        val (id, deps) = (job, version).tupled match {
          // Handling case of job and version being None
          case None =>
            val sendSnapshot = events.size >= Threshold
            val jobInstance @ Job(id, _, _) = EventHandler[JobEvent, Job].applyEvents(events)

            val loadMock = JobRepositoryMock.Load(equalTo(id), value(None))
            val readSnapshotMock = MockSnapshotStore.ReadSnapshot(equalTo(id), value(None))
            val saveMock = JobRepositoryMock.Save(equalTo(jobInstance), value(1L))
            val saveSnapshotMock = MockSnapshotStore.SaveSnapshot(equalTo((id.asKey, maxChange.version)), value(1L))
            val readEventsMock = MockJobEventStore.ReadEvents.Full(equalTo(id.asKey), value(events.some))

            val layers =
              if sendSnapshot then
                ((loadMock ++ readSnapshotMock) || (readSnapshotMock ++ loadMock)) ++ readEventsMock ++
                ((saveMock ++ saveSnapshotMock) || (saveSnapshotMock ++ saveMock))
              else ((loadMock ++ readSnapshotMock) || (readSnapshotMock ++ loadMock)) ++ readEventsMock

            (id, layers.toLayer)
          case Some((jobInstance @ Job(id, _, _), v)) =>
            // Other cases similar to IngestionService
            // Convenient MockConfig similar to what's shown above
            ???
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
            PostgresCQRSPersistence.live(),
            JobEventStore.live(),
            SnapshotStore.live(),
            SnapshotStrategy.live(),
            JobService.live(),
          )
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
            PostgresCQRSPersistence.live(),
            JobEventStore.live(),
            SnapshotStore.live(),
            SnapshotStrategy.live(),
            JobService.live(),
          )
        )
      }
    },
    test("should start a job using JobCQRS") {
      check(jobIdGen, timestampGen, jobMeasurementGen, jobTagGen) { (id, timestamp, total, tag) =>
        val mockEnv = JobRepoMock.StartJob(
          equalTo((id, timestamp, total, tag)),
          unit
        )

        val testLayer = mockEnv.toLayer >>> Transactional.live >>> JobService.layer

        (for {
          _ <- JobService.startJob(id, timestamp, total, tag)
        } yield assertCompletes)
          .provideLayer(testLayer)
      }
    },
    test("should report progress of a job using JobCQRS") {
      check(jobIdGen, timestampGen, progressGen) { (id, timestamp, progress) =>
        val mockEnv = JobRepoMock.ReportProgress(
          equalTo((id, timestamp, progress)),
          unit
        )

        val testLayer = mockEnv.toLayer >>> Transactional.live >>> JobService.layer

        (for {
          _ <- JobService.reportProgress(id, timestamp, progress)
        } yield assertCompletes)
          .provideLayer(testLayer)
      }
    },
    test("should complete a job using JobCQRS") {
      check(jobIdGen, timestampGen, completionReasonGen) { (id, timestamp, reason) =>
        val mockEnv = JobRepoMock.CompleteJob(
          equalTo((id, timestamp, reason)),
          unit
        )

        val testLayer = mockEnv.toLayer >>> Transactional.live >>> JobService.layer

        (for {
          _ <- JobService.completeJob(id, timestamp, reason)
        } yield assertCompletes)
          .provideLayer(testLayer)
      }
    }

  ).provideSomeLayerShared(
    ZConnectionMock.pool() ++ (zio.telemetry.opentelemetry.OpenTelemetry.contextZIO >>> tracingMockLayer()),
  ) @@ TestAspect.withLiveClock
}


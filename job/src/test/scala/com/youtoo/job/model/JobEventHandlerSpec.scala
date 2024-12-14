package com.youtoo
package job
package model

import com.youtoo.cqrs.Codecs.given

import zio.*
import zio.mock.*
import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*
import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*

object JobEventHandlerSpec extends MockSpecDefault {

  def spec = suite("JobEventHandlerSpec")(
    test("Applying JobStarted event initializes the state") {
      check(jobIdGen, versionGen, timestampGen, jobMeasurementGen, jobTagGen) { (jobId, v1, timestamp, total, tag) =>
        val event = Change(v1, JobEvent.JobStarted(jobId, timestamp, total, tag))
        val state = EventHandler.applyEvents(NonEmptyList(event))

        val expectedState = Job(
          jobId,
          tag,
          total,
          JobStatus.Running(timestamp, timestamp, Progress(0)),
        )
        assert(state)(equalTo(expectedState))
      }
    },
    test("Applying ProgressReported event updates progress") {
      check(jobIdGen, versionGen, versionGen, timestampGen, jobMeasurementGen, jobTagGen, progressGen) {
        (jobId, v1, v2, timestamp, total, tag, progress) =>
          val events = NonEmptyList(
            Change(v1, JobEvent.JobStarted(jobId, timestamp, total, tag)),
            Change(v2, JobEvent.ProgressReported(jobId, timestamp, progress)),
          )
          val state = EventHandler.applyEvents(events)

          val expectedState = Job(
            jobId,
            tag,
            total,
            JobStatus.Running(timestamp, timestamp, progress),
          )
          assert(state)(equalTo(expectedState))
      }
    },
    test("Applying JobCompleted event transitions to Completed status") {
      check(jobIdGen, versionGen, versionGen, timestampGen, jobMeasurementGen, jobTagGen, jobCompletionReasonGen) {
        (jobId, v1, v2, timestamp, total, tag, reason) =>
          val events = NonEmptyList(
            Change(v1, JobEvent.JobStarted(jobId, timestamp, total, tag)),
            Change(v2, JobEvent.JobCompleted(jobId, timestamp, reason)),
          )
          val state = EventHandler.applyEvents(events)

          val initialStatus: JobStatus.Running = JobStatus.Running(timestamp, timestamp, Progress(0))
          val expectedState = Job(
            jobId,
            tag,
            total,
            JobStatus.Completed(initialStatus, timestamp, reason),
          )
          assert(state)(equalTo(expectedState))
      }
    },
    test("Applying the same JobStarted event multiple times should fail") {
      check(jobIdGen, versionGen, timestampGen, jobMeasurementGen, jobTagGen) { (jobId, v1, timestamp, total, tag) =>
        val event = Change(v1, JobEvent.JobStarted(jobId, timestamp, total, tag))
        val state1 = EventHandler.applyEvents(NonEmptyList(event))
        val exit = ZIO.attempt(EventHandler.applyEvents(state1, NonEmptyList(event))).exit
        assertZIO(exit)(failsWithA[IllegalArgumentException])
      }
    },
    test("Applying events out of order throws an exception") {
      check(jobIdGen, versionGen, versionGen, timestampGen, jobMeasurementGen, jobTagGen, progressGen) {
        (jobId, v1, v2, timestamp, total, tag, progress) =>
          val events = NonEmptyList(
            Change(v1, JobEvent.ProgressReported(jobId, timestamp, progress)),
            Change(v2, JobEvent.JobStarted(jobId, timestamp, total, tag)),
          )
          assertZIO(ZIO.attempt(EventHandler.applyEvents(events)).exit)(failsWithA[IllegalArgumentException])
      }
    },
  ) @@ TestAspect.withLiveClock
}

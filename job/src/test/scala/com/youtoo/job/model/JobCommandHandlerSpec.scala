package com.youtoo
package job
package model

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*

object JobCommandSpec extends ZIOSpecDefault {
  val handler = summon[JobCommandHandler]

  def spec = suite("JobCommandHandlerSpec")(
    test("StartJob command produces JobStarted event") {
      check(jobIdGen, timestampGen, jobMeasurementGen, jobTagGen) { (id, timestamp, total, tag) =>
        val command = JobCommand.StartJob(id, timestamp, total, tag)
        val events = handler.applyCmd(command)
        val expectedEvent = JobEvent.JobStarted(id, timestamp, total, tag)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("ReportProgress command produces ProgressReported event") {
      check(jobIdGen, timestampGen, progressGen) { (id, timestamp, progress) =>
        val command = JobCommand.ReportProgress(id, timestamp, progress)
        val events = handler.applyCmd(command)
        val expectedEvent = JobEvent.ProgressReported(id, timestamp, progress)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("CompleteJob command produces JobCompleted event") {
      check(jobIdGen, timestampGen, jobCompletionReasonGen) { (id, timestamp, reason) =>
        val command = JobCommand.CompleteJob(id, timestamp, reason)
        val events = handler.applyCmd(command)
        val expectedEvent = JobEvent.JobCompleted(id, timestamp, reason)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("Applying the same command multiple times produces the same event") {
      check(jobIdGen, timestampGen, jobMeasurementGen, jobTagGen) { (id, timestamp, total, tag) =>
        val command = JobCommand.StartJob(id, timestamp, total, tag)
        val events1 = handler.applyCmd(command)
        val events2 = handler.applyCmd(command)
        assert(events1)(equalTo(events2))
      }
    },
  ) @@ TestAspect.withLiveClock
}

package com.youtoo
package job
package model

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.*
import zio.prelude.*
import zio.test.Assertion.*
import zio.test.*

import com.youtoo.cqrs.Codecs.given

object LoadIsCancelledSpec extends ZIOSpecDefault {

  def spec = suite("LoadIsCancelledSpec")(
    test("should return true if JobCompleted event with Cancellation reason exists") {
      check(jobIdGen, timestampGen, versionGen) { (jobId, timestamp, version) =>
        val handler = new JobEvent.LoadIsCancelled()
        val events = NonEmptyList(
          Change(
            version,
            JobEvent.JobCompleted(jobId, timestamp, Job.CompletionReason.Cancellation()),
          ),
        )
        val result = handler.applyEvents(events)
        assert(result)(isTrue)
      }
    },
    test("should return false if JobCompleted event with non-Cancellation reason exists") {
      check(jobIdGen, timestampGen, versionGen) { (jobId, timestamp, version) =>
        val handler = new JobEvent.LoadIsCancelled()
        val events = NonEmptyList(
          Change(
            version,
            JobEvent.JobCompleted(jobId, timestamp, Job.CompletionReason.Success()),
          ),
        )
        val result = handler.applyEvents(events)
        assert(result)(isFalse)
      }
    },
    test("should return false if no JobCompleted event exists") {
      check(jobIdGen, timestampGen, versionGen) { (jobId, timestamp, version) =>
        val handler = new JobEvent.LoadIsCancelled()
        val events = NonEmptyList(
          Change(
            version,
            JobEvent.JobStarted(jobId, timestamp, JobMeasurement.Variable(), Job.Tag("test")),
          ),
        )
        val result = handler.applyEvents(events)
        assert(result)(isFalse)
      }
    },
    test("should return true if a cancellation event is present among other events") {
      check(jobIdGen, versionGen, versionGen, timestampGen, versionGen, jobTagGen) {
        (jobId, v0, v1, timestamp, version, tag) =>
          val handler = new JobEvent.LoadIsCancelled()
          val events = NonEmptyList(
            Change(v0, JobEvent.JobStarted(jobId, timestamp, JobMeasurement.Variable(), tag)),
            Change(v1, JobEvent.JobCompleted(jobId, timestamp, Job.CompletionReason.Cancellation())),
          )
          val result = handler.applyEvents(events)
          assert(result)(isTrue)
      }
    },
  )
}

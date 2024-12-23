package com.youtoo
package job
package model

import zio.test.*
import zio.test.Assertion.*
import com.youtoo.job.model.*

object JobModelSpec extends ZIOSpecDefault {

  def spec = suite("Job Model Tests")(
    testIsCancelled,
    testIsCompleted,
    testCreatedTimestamp,
    testLastModifiedTimestamp,
  )

  val testIsCancelled = suite("isCancelled()")(
    test("should return true for JobStatus.Completed when reason is cancellation") {
      val runningStatus: JobStatus.Running = JobStatus.Running(Timestamp(1L), Timestamp(2L), Progress(50))
      val completedStatus = JobStatus.Completed(runningStatus, Timestamp(3L), Job.CompletionReason.Cancellation())

      assert(completedStatus.isCancelled)(isTrue)
    },
    test("should return false for JobStatus.Running") {
      val runningStatus = JobStatus.Running(Timestamp(1L), Timestamp(2L), Progress(50))

      assert(runningStatus.isCancelled)(isFalse)
    },
  )

  val testIsCompleted = suite("isCompleted()")(
    test("should return true for JobStatus.Completed") {
      val runningStatus: JobStatus.Running = JobStatus.Running(Timestamp(1L), Timestamp(2L), Progress(50))
      val completedStatus = JobStatus.Completed(runningStatus, Timestamp(3L), Job.CompletionReason.Success())

      assert(completedStatus.isCompleted)(isTrue)
    },
    test("should return false for JobStatus.Running") {
      val runningStatus = JobStatus.Running(Timestamp(1L), Timestamp(2L), Progress(50))

      assert(runningStatus.isCompleted)(isFalse)
    },
  )

  val testCreatedTimestamp = suite("created")(
    test("should return the start timestamp for JobStatus.Running") {
      val runningStatus = JobStatus.Running(Timestamp(1L), Timestamp(2L), Progress(50))
      val job = Job(Job.Id(Key(123L)), Job.Tag("test"), JobMeasurement.Variable(), runningStatus)

      assert(job.created)(equalTo(Timestamp(1L)))
    },
    test("should return the start timestamp from JobStatus.Running in JobStatus.Completed") {
      val runningStatus: JobStatus.Running = JobStatus.Running(Timestamp(1L), Timestamp(2L), Progress(50))
      val completedStatus = JobStatus.Completed(runningStatus, Timestamp(3L), Job.CompletionReason.Success())
      val job = Job(Job.Id(Key(123L)), Job.Tag("test"), JobMeasurement.Variable(), completedStatus)

      assert(job.created)(equalTo(Timestamp(1L)))
    },
  )

  val testLastModifiedTimestamp = suite("lastModified")(
    test("should return the last updated timestamp for JobStatus.Running") {
      val runningStatus = JobStatus.Running(Timestamp(1L), Timestamp(2L), Progress(50))
      val job = Job(Job.Id(Key(123L)), Job.Tag("test"), JobMeasurement.Variable(), runningStatus)

      assert(job.lastModified)(equalTo(Timestamp(2L)))
    },
    test("should return the completed timestamp for JobStatus.Completed") {
      val runningStatus: JobStatus.Running = JobStatus.Running(Timestamp(1L), Timestamp(2L), Progress(50))
      val completedStatus = JobStatus.Completed(runningStatus, Timestamp(3L), Job.CompletionReason.Success())
      val job = Job(Job.Id(Key(123L)), Job.Tag("test"), JobMeasurement.Variable(), completedStatus)

      assert(job.lastModified)(equalTo(Timestamp(3L)))
    },
  )
}

package com.youtoo
package job

import com.youtoo.cqrs.Codecs.given

import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*

import com.youtoo.job.model.*

import com.youtoo.cqrs.Codecs.given

inline def isPayload[Event](key: Key, payload: Event) = assertion[(Key, Change[Event])]("isPayload") { case (id, ch) =>
  id == key && ch.payload == payload
}

given versionGen: Gen[Any, Version] = Gen.fromZIO(Version.gen.orDie)

given keyGen: Gen[Any, Key] = Gen.fromZIO(Key.gen.orDie)

given jobIdGen: Gen[Any, Job.Id] = Gen.fromZIO(Job.Id.gen.orDie)

// Assuming you have timestampGen as it was showcased earlier:
given timestampGen: Gen[Any, Timestamp] = Gen.fromZIO(Timestamp.now)

// For progress, we assume it holds a long value. Let's limit between 0 and a reasonable upper bound.
given progressGen: Gen[Any, Progress] = Gen.long(0L, 1000L).map(Progress.apply)

val deterministicMeasurementGen: Gen[Any, JobMeasurement.Deterministic] =
  Gen.long(0, 10000).map(JobMeasurement.Deterministic.apply)

val variableMeasurementGen: Gen[Any, JobMeasurement.Variable] =
  Gen.const(JobMeasurement.Variable())

val jobMeasurementGen: Gen[Any, JobMeasurement] = Gen.oneOf(
  deterministicMeasurementGen,
  variableMeasurementGen
)

val jobStatusRunningGen: Gen[Any, JobStatus.Running] =
  for {
    started <- timestampGen
    lastUpdated <- timestampGen
    progress <- progressGen
  } yield JobStatus.Running(started, lastUpdated, progress)

val jobReasonGen: Gen[Any, Job.CompletionReason] = Gen.oneOf(
  Gen.const(Job.CompletionReason.Success()),
  Gen.alphaNumericStringBounded(5, 100).map(Job.CompletionReason.Failure.apply)
)

val jobStatusCompletedGen: Gen[Any, JobStatus.Completed] =
  for {
    running <- jobStatusRunningGen
    timestamp <- timestampGen
    reason <- jobReasonGen
  } yield JobStatus.Completed(running, timestamp, reason)

val jobStatusGen: Gen[Any, JobStatus] = Gen.oneOf(
  jobStatusRunningGen,
  jobStatusCompletedGen
)

val jobTagGen: Gen[Any, Job.Tag] =
  Gen.alphaNumericStringBounded(5, 10).map(Job.Tag.apply)

given jobGen: Gen[Any, Job] =
  for {
    id <- jobIdGen
    tag <- jobTagGen
    total <- jobMeasurementGen
    status <- jobStatusGen
  } yield Job(id, tag, total, status)

val startJobCommandGen: Gen[Any, JobCommand.StartJob] =
  for {
    id <- jobIdGen
    timestamp <- timestampGen
    total <- jobMeasurementGen
    tag <- jobTagGen
  } yield JobCommand.StartJob(id, timestamp, total, tag)

val reportProgressCommandGen: Gen[Any, JobCommand.ReportProgress] =
  for {
    id <- jobIdGen
    timestamp <- timestampGen
    progress <- progressGen
  } yield JobCommand.ReportProgress(id, timestamp, progress)

val doneCommandGen: Gen[Any, JobCommand.Done] =
  for {
    id <- jobIdGen
    timestamp <- timestampGen
    reason <- jobReasonGen
  } yield JobCommand.Done(id, timestamp, reason)

val jobCommandGen: Gen[Any, JobCommand] = Gen.oneOf(
  startJobCommandGen,
  reportProgressCommandGen,
  doneCommandGen
)

val jobStartedEventGen: Gen[Any, JobEvent.JobStarted] =
  for {
    id <- jobIdGen
    timestamp <- timestampGen
    total <- jobMeasurementGen
    tag <- jobTagGen
  } yield JobEvent.JobStarted(id, timestamp, total, tag)

val progressReportedEventGen: Gen[Any, JobEvent.ProgressReported] =
  for {
    id <- jobIdGen
    timestamp <- timestampGen
    progress <- progressGen
  } yield JobEvent.ProgressReported(id, timestamp, progress)

val jobCompletedEventGen: Gen[Any, JobEvent.JobCompleted] =
  for {
    id <- jobIdGen
    timestamp <- timestampGen
    reason <- jobReasonGen
  } yield JobEvent.JobCompleted(id, timestamp, reason)

val jobEventGen: Gen[Any, JobEvent] = Gen.oneOf(
  jobStartedEventGen,
  progressReportedEventGen,
  jobCompletedEventGen
)

val changeEventGen: Gen[Any, Change[JobEvent]] =
  (versionGen <*> jobEventGen).map(Change.apply)

val eventSequenceGen: Gen[Any, NonEmptyList[Change[JobEvent]]] =
  for {
    events <- Gen.listOf(jobEventGen)
    if events.nonEmpty
    changes <- Gen.fromZIO {
      ZIO.foreach(events) { event =>
        for {
          v <- Version.gen.orDie
        } yield Change(v, event)
      }
    }
  } yield NonEmptyList(changes.head, changes.tail*)

val jobStartedChangeGen: Gen[Any, Change[JobEvent]] =
  for {
    id <- jobIdGen
    timestamp <- timestampGen
    measurement <- jobMeasurementGen
    tag <- jobTagGen
    version <- versionGen
  } yield Change(version, JobEvent.JobStarted(id, timestamp, measurement, tag))

val followupEventGen: Gen[Any, JobEvent] = Gen.oneOf(
  progressReportedEventGen,
  jobCompletedEventGen
)

val validEventSequenceGen: Gen[Any, NonEmptyList[Change[JobEvent]]] =
  for {
    startChange <- jobStartedChangeGen
    otherEvents <- Gen.listOf(followupEventGen)
    changes <- Gen.fromZIO {
      ZIO.foreach(otherEvents) { event =>
        for {
          version <- Version.gen.orDie
        } yield Change(version, event)
      }
    }
  } yield NonEmptyList(startChange, changes*)


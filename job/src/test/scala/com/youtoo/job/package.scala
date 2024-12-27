package com.youtoo
package job

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
given timestampGen: Gen[Any, Timestamp] = Gen.fromZIO(Timestamp.gen)

// For progress, we assume it holds a long value. Let's limit between 0 and a reasonable upper bound.
given progressGen: Gen[Any, Progress] = Gen.long(0L, 1000L).map(Progress.apply)

val deterministicMeasurementGen: Gen[Any, JobMeasurement.Deterministic] =
  Gen.long(0, 10000).map(JobMeasurement.Deterministic.apply)

val variableMeasurementGen: Gen[Any, JobMeasurement.Variable] =
  Gen.const(JobMeasurement.Variable())

val jobMeasurementGen: Gen[Any, JobMeasurement] = Gen.oneOf(
  deterministicMeasurementGen,
  variableMeasurementGen,
)

val jobStatusRunningGen: Gen[Any, JobStatus.Running] =
  for {
    started <- timestampGen
    lastUpdated <- timestampGen
    progress <- progressGen
  } yield JobStatus.Running(started, lastUpdated, progress)

val jobCompletionReasonGen: Gen[Any, Job.CompletionReason] = Gen.oneOf(
  Gen.const(Job.CompletionReason.Success()),
  Gen.option(Gen.alphaNumericStringBounded(5, 100)).map(Job.CompletionReason.Failure.apply),
)

val jobStatusCompletedGen: Gen[Any, JobStatus.Completed] =
  for {
    running <- jobStatusRunningGen
    timestamp <- timestampGen
    reason <- jobCompletionReasonGen
  } yield JobStatus.Completed(running, timestamp, reason)

val jobStatusGen: Gen[Any, JobStatus] = Gen.oneOf(
  jobStatusRunningGen,
  jobStatusCompletedGen,
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

val startJobCommandGen: Gen[Any, JobCommand] =
  for {
    id <- jobIdGen
    timestamp <- timestampGen
    total <- jobMeasurementGen
    tag <- jobTagGen
  } yield JobCommand.StartJob(id, timestamp, total, tag)

val reportProgressCommandGen: Gen[Any, JobCommand] =
  for {
    id <- jobIdGen
    timestamp <- timestampGen
    progress <- progressGen
  } yield JobCommand.ReportProgress(id, timestamp, progress)

val cancelJobCommandGen: Gen[Any, JobCommand] =
  for {
    id <- jobIdGen
    timestamp <- timestampGen
  } yield JobCommand.CancelJob(id, timestamp)

val doneCommandGen: Gen[Any, JobCommand.CompleteJob] =
  for {
    id <- jobIdGen
    timestamp <- timestampGen
    reason <- jobCompletionReasonGen
  } yield JobCommand.CompleteJob(id, timestamp, reason)

val jobCommandGen: Gen[Any, JobCommand] = Gen.oneOf(
  startJobCommandGen,
  cancelJobCommandGen,
  reportProgressCommandGen,
  doneCommandGen,
)

val validCommandSequenceGen: Gen[Any, NonEmptyList[JobCommand]] =
  for {
    startCmd <- startJobCommandGen
    progressCmds <- Gen.oneOf(
      Gen.listOf(reportProgressCommandGen),
      cancelJobCommandGen.map(List(_)),
    )
    done <- doneCommandGen
    cmds = progressCmds ::: (done :: Nil)
  } yield NonEmptyList(startCmd, cmds*)

val jobStartedEventGen: Gen[Any, JobEvent] =
  for {
    id <- jobIdGen
    timestamp <- timestampGen
    total <- jobMeasurementGen
    tag <- jobTagGen
  } yield JobEvent.JobStarted(id, timestamp, total, tag)

val progressReportedEventGen: Gen[Any, JobEvent] =
  for {
    id <- jobIdGen
    timestamp <- timestampGen
    progress <- progressGen
  } yield JobEvent.ProgressReported(id, timestamp, progress)

val jobCompletedEventGen: Gen[Any, JobEvent] =
  for {
    id <- jobIdGen
    timestamp <- timestampGen
    reason <- jobCompletionReasonGen
  } yield JobEvent.JobCompleted(id, timestamp, reason)

val jobCompletedEventChangeGen: Gen[Any, Change[JobEvent]] =
  for {
    version <- versionGen
    id <- jobIdGen
    timestamp <- timestampGen
    reason <- jobCompletionReasonGen
  } yield Change(version, JobEvent.JobCompleted(id, timestamp, reason))

val jobEventGen: Gen[Any, JobEvent] = Gen.oneOf(
  jobStartedEventGen,
  progressReportedEventGen,
  jobCompletedEventGen,
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

val validEventSequenceGen: Gen[Any, NonEmptyList[Change[JobEvent]]] =
  for {
    startChange <- jobStartedChangeGen
    otherEvents <- Gen.listOf(progressReportedEventGen)
    progressChanges <- Gen.fromZIO {
      ZIO.foreach(otherEvents) { event =>
        for {
          version <- Version.gen.orDie
        } yield Change(version, event)
      }
    }
    done <- jobCompletedEventGen
    version <- versionGen
    changes = progressChanges ::: (Change(version, done) :: Nil)
  } yield NonEmptyList(startChange, changes*)

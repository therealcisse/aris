package com.youtoo
package job
package model

import zio.*
import zio.prelude.*

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.*
import zio.schema.*

enum JobEvent {
  case JobStarted(id: Job.Id, timestamp: Timestamp, total: JobMeasurement, tag: Job.Tag)
  case ProgressReported(id: Job.Id, timestamp: Timestamp, progress: Progress)
  case JobCompleted(id: Job.Id, timestamp: Timestamp, reason: Job.CompletionReason)
}

type JobEventHandler = EventHandler[JobEvent, Job]

object JobEvent {
  val discriminator: Discriminator = Discriminator("Job")

  given Schema[JobEvent] = DeriveSchema.gen

  given MetaInfo[JobEvent] {
    extension (self: JobEvent)
      def namespace: Namespace = self match {
        case JobEvent.JobStarted(_, _, _, _)    => NS.JobStarted
        case JobEvent.ProgressReported(_, _, _) => NS.ProgressReported
        case JobEvent.JobCompleted(_, _, _)     => NS.JobCompleted
      }

    extension (self: JobEvent) def hierarchy = None
    extension (self: JobEvent) def props = Chunk.empty
    extension (self: JobEvent) def reference = None
  }

  object NS {
    val JobStarted = Namespace(0)
    val ProgressReported = Namespace(100)
    val JobCompleted = Namespace(300)
  }

  given JobEventHandler {
    def applyEvents(events: NonEmptyList[Change[JobEvent]]): Job = {
      events match {
        case NonEmptyList.Single(ch) =>
          ch.payload match {
            case JobEvent.JobStarted(id, timestamp, total, tag) =>
              Job(
                id,
                tag,
                total,
                JobStatus.Running(started = timestamp, lastUpdated = timestamp, progress = Progress(0)),
              )

            case _ => throw IllegalArgumentException("Unexpected event, current state is empty")
          }

        case NonEmptyList.Cons(ch, ls) =>
          ch.payload match {
            case JobEvent.JobStarted(id, timestamp, total, tag) =>
              applyEvents(
                zero = Job(
                  id,
                  tag,
                  total,
                  JobStatus.Running(started = timestamp, lastUpdated = timestamp, progress = Progress(0)),
                ),
                ls,
              )

            case _ => throw IllegalArgumentException("Unexpected event, current state is empty")
          }
      }
    }

    def applyEvents(zero: Job, events: NonEmptyList[Change[JobEvent]]): Job = {
      events.foldLeft(zero) { (state, event) =>
        event.payload match {
          case JobEvent.JobStarted(_, _, _, _) =>
            throw IllegalArgumentException(s"Unexpected start event in current state: ${event.payload.getClass.getName}")

          case JobEvent.ProgressReported(_, timestamp, progress) =>
            state.status match {
              case JobStatus.Running(started, _, _) =>
                state.copy(status = JobStatus.Running(started, lastUpdated = timestamp, progress))
              case _ =>
                throw IllegalArgumentException(s"Progress event in invalid state: ${state.status}")
            }

          case JobEvent.JobCompleted(_, timestamp, reason) =>
            state.status match {
              case status @ JobStatus.Running(_, _, _) =>
                state.copy(status = JobStatus.Completed(execution = status, timestamp, reason))
              case _ =>
                throw IllegalArgumentException(s"Completion event in invalid state: ${state.status}")
            }
        }
      }
    }
  }
}


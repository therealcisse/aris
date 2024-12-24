package com.youtoo
package job
package model

import zio.*
import zio.prelude.*
import zio.schema.*

import cats.Order

case class Job(
  id: Job.Id,
  tag: Job.Tag,
  total: JobMeasurement,
  status: JobStatus,
)

object Job {
  given Schema[Job] = DeriveSchema.gen

  type Id = Id.Type
  object Id extends Newtype[Key] {
    def gen: Task[Id] = Key.gen.map(wrap)
    def apply(value: Long): Id = Id(Key(value))
    extension (a: Id) inline def asKey: Key = Id.unwrap(a)
    given Schema[Id] = derive
  }

  type Tag = Tag.Type
  object Tag extends Newtype[String] {
    extension (a: Tag) inline def value: String = Tag.unwrap(a)
    given Schema[Tag] = derive
  }

  enum CompletionReason {
    case Success()
    case Cancellation()
    case Failure(message: String)
  }

  object CompletionReason {
    given Schema[CompletionReason] = DeriveSchema.gen

    extension (reason: CompletionReason)
      def isCancellation(): Boolean = reason match {
        case CompletionReason.Cancellation() => true
        case _ => false
      }

  }

  extension (job: Job) {
    def created: Timestamp = job.status match {
      case JobStatus.Running(started, _, _) => started
      case JobStatus.Completed(execution, _, _) => execution.started
    }

    def lastModified: Timestamp = job.status match {
      case JobStatus.Running(_, lastUpdated, _) => lastUpdated
      case JobStatus.Completed(_, completed, _) => completed
    }
  }

  given Order[Job] = Order.by(_.id.asKey)
}

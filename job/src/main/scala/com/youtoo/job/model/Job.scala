package com.youtoo
package job
package model

import zio.*
import zio.prelude.*
import zio.schema.*

case class Job(id: Job.Id, tag: Job.Tag, total: JobMeasurement, status: JobStatus)

object Job {
  given Schema[Job] = DeriveSchema.gen

  type Id = Id.Type

  object Id extends Newtype[Key] {
    import zio.schema.*

    def gen: Task[Id] = Key.gen.map(wrap)

    def apply(value: Long): Id = Id(Key(value))

    extension (a: Id) inline def asKey: Key = Id.unwrap(a)

    given Schema[Id] = derive
  }

  type Tag = Tag.Type
  object Tag extends Newtype[String] {
    import zio.schema.*

    extension (a: Tag) inline def value: String = Tag.unwrap(a)

    given Schema[Tag] = derive
  }

  enum CompletionReason {
    case Success()
    case Failure(message: String)
  }

  object CompletionReason {

    given Schema[CompletionReason] = DeriveSchema.gen

  }
}

package com.youtoo.cqrs
package example
package model

import cats.implicits.*

import zio.*

import zio.prelude.*
import zio.schema.*

case class Ingestion(id: Ingestion.Id, status: Ingestion.Status, timestamp: Timestamp)

object Ingestion {
  given Schema[Ingestion] = DeriveSchema.gen

  type Id = Id.Type

  object Id extends Newtype[Key] {
    import zio.schema.*

    def gen: Task[Id] = Key.gen.map(wrap)

    extension (a: Id) inline def asKey: Key = Id.unwrap(a)

    given Schema[Id] = Schema
      .primitive[String]
      .transform(
        Key.wrap `andThen` wrap,
        unwrap `andThen` Key.unwrap,
      )

  }

  def empty(id: Ingestion.Id): Task[Ingestion] =
    Timestamp.now map { timestamp =>
      Ingestion(
        id = id,
        status = Ingestion.Status.Initial(),
        timestamp = timestamp,
      )
    }

  enum Status {
    case Initial()
    case Resolved(files: Set[String])
    case Processing(remaining: Set[String], processed: Set[String], failed: Set[String])
    case Completed(files: Set[String], failed: Set[String])

  }

  object Status {
    inline def empty: Status = Status.Initial()

    given Schema[Status] = DeriveSchema.gen

  }
}

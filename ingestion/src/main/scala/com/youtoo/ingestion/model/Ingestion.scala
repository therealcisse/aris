package com.youtoo.cqrs
package ingestion
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

    def apply(value: String): Id = Id(Key(value))

    extension (a: Id) inline def asKey: Key = Id.unwrap(a)

    given Schema[Id] = Schema
      .primitive[String]
      .transform(
        Key.wrap `andThen` wrap,
        unwrap `andThen` Key.unwrap,
      )

  }

  enum Status {
    case Initial()
    case Resolved(files: NonEmptySet[String])
    case Processing(remaining: Set[String], processing: Set[String], processed: Set[String], failed: Set[String])
    case Completed(files: NonEmptySet[String])
    case Failed(done: Set[String], failed: NonEmptySet[String])
    case Stopped(processing: Status.Processing, timestamp: Timestamp)

    def isSuccessful: Status = this match {
      case Processing(r, pp, p, f) if pp.isEmpty && r.isEmpty && f.isEmpty =>
        NonEmptySet.fromIterableOption(p) match {
          case None => this
          case Some(nes) => Completed(nes)
        }

      case Processing(r, pp, p, f) if pp.isEmpty && r.isEmpty && !f.isEmpty =>
        NonEmptySet.fromIterableOption(f) match {
          case None => this
          case Some(nes) => Failed(p, nes)
        }

      case s => s
    }

  }

  extension (status: Status)
    def totalFiles: Option[NonEmptySet[String]] =
      status match {
        case Status.Initial() => None
        case Status.Completed(files) => files.some
        case Status.Failed(done, failed) => NonEmptySet.fromIterableOption(done ++ failed)
        case Status.Processing(remaining, processing, processed, failed) =>
          NonEmptySet.fromIterableOption(remaining ++ processing ++ processed ++ failed)
        case Status.Resolved(files) => files.some
        case Status.Stopped(processing: Ingestion.Status, _) => processing.totalFiles

      }

  extension (status: Status)
    infix def is(s: String): Boolean =
      status match {
        case Status.Initial() => s == "initial"
        case Status.Completed(_) => s == "completed"
        case Status.Failed(_, _) => s == "failed"
        case Status.Processing(_, _, _, _) =>
          s == "processing"
        case Status.Resolved(_) => s == "resolved"
        case Status.Stopped(_, _) => s == "processing"

      }

  object Status {
    inline def empty: Status = Status.Initial()

    given Schema[Status] = DeriveSchema.gen

  }
}

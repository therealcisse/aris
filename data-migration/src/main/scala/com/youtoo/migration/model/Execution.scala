package com.youtoo.cqrs
package migration
package model

import cats.Order

import cats.implicits.*

import zio.*

import zio.prelude.*
import zio.schema.*

enum Execution {
  case Stopped(processing: Execution.Processing, timestamp: Timestamp)
  case Finished(processing: Execution.Processing, timestamp: Timestamp)
  case Processing(id: Execution.Id, stats: Stats, timestamp: Timestamp)

  def startTime: Timestamp = this match {
    case processing: Execution.Processing => processing.timestamp
    case stopped: Execution.Stopped => stopped.timestamp
    case finished: Execution.Finished => finished.timestamp
  }

  def endTime: Option[Timestamp] = this match {
    case _: Execution.Processing => None
    case stopped: Execution.Stopped => Some(stopped.timestamp)
    case finished: Execution.Finished => Some(finished.timestamp)
  }

  def status: ExecutionStatus = this match {
    case _: Execution.Processing => ExecutionStatus.running
    case _: Execution.Stopped => ExecutionStatus.stopped
    case finished: Execution.Finished =>
      if finished.processing.stats.failed.nonEmpty then ExecutionStatus.failed else ExecutionStatus.success
  }

}

object Execution {
  given Schema[Execution] = DeriveSchema.gen

  type Id = Id.Type

  object Id extends Newtype[Key] {
    import zio.schema.*

    def gen: Task[Id] = Key.gen.map(Id.wrap)

    extension (a: Id) inline def asKey: Key = Id.unwrap(a)

    given Schema[Id] = Schema
      .primitive[String]
      .transform(
        Key.wrap `andThen` wrap,
        unwrap `andThen` Key.unwrap,
      )

    given Order[Id] = Order.by(_.asKey)

  }

}

package com.youtoo
package migration
package model

import cats.implicits.*

import zio.*

import zio.prelude.*
import zio.schema.*

case class Migration(id: Migration.Id, state: Migration.State, timestamp: Timestamp)

object Migration {
  given Schema[Migration] = DeriveSchema.gen

  type Id = Id.Type

  object Id extends Newtype[Key] {
    import zio.schema.*

    def gen: Task[Id] = Key.gen.map(Id.wrap)

    extension (a: Id) inline def asKey: Key = Id.unwrap(a)

    given Schema[Id] = Schema
      .primitive[Long]
      .transform(
        Key.wrap `andThen` wrap,
        unwrap `andThen` Key.unwrap,
      )

  }

  extension (migration: Migration)
    def totalProcessed: Long = migration.state.executions.values.foldLeft(0L)((l, n) => l + n.totalProcessed)
  extension (migration: Migration)
    def keys: Set[Key] = migration.state.executions.values.foldLeft(Set.empty)((l, n) => l ++ n.keys)

  case class State(executions: Map[Execution.Id, Execution]) {

    lazy val lastExecution: Option[Execution] =
      executions.keys.maxOption flatMap { key =>
        executions.get(key)
      }

    def status: ExecutionStatus = lastExecution.fold(ExecutionStatus.registered) { execution =>
      execution match {
        case _: Execution.Processing => ExecutionStatus.running
        case _: Execution.Stopped => ExecutionStatus.stopped
        case finished: Execution.Finished =>
          if finished.processing.stats.failed.nonEmpty then ExecutionStatus.failed else ExecutionStatus.success
        case _: Execution.Failed =>
          ExecutionStatus.execution_failed
      }
    }

    export executions.isEmpty
  }

  object State {
    inline def empty: State = State(executions = Map())

    given Schema[State] = DeriveSchema.gen

  }
}

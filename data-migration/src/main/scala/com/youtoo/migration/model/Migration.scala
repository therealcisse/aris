package com.youtoo.cqrs
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
      .primitive[String]
      .transform(
        Key.wrap `andThen` wrap,
        unwrap `andThen` Key.unwrap,
      )

  }

  case class State(executions: Map[Execution.Id, Execution]) {

    val lastExecution: Option[Execution] =
      executions.keys.maxOption flatMap { key =>
        executions.get(key)
      }

    export executions.isEmpty
  }

  object State {
    inline def empty: State = State(executions = Map())

    given Schema[State] = DeriveSchema.gen

  }
}

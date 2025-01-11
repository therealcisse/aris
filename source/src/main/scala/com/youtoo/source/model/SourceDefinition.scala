package com.youtoo
package source
package model

import zio.*

import zio.prelude.*
import zio.schema.*

case class SourceDefinition(id: SourceDefinition.Id, info: SourceType, created: Timestamp, updated: Option[Timestamp])

object SourceDefinition {
  type Id = Id.Type

  object Id extends Newtype[Key] {
    def gen: Task[Id] = Key.gen.map(wrap)
    def apply(value: Long): Id = Id(Key(value))
    extension (a: Id) inline def asKey: Key = Id.unwrap(a)
    given Schema[Type] = derive
  }

  given Schema[SourceDefinition] = DeriveSchema.gen
}

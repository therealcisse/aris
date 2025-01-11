package com.youtoo
package ingestion
package model

import zio.*
import zio.schema.*
import zio.prelude.*

import com.youtoo.sink.model.*
import com.youtoo.source.model.*

case class IngestionConfig(
  connections: Chunk[IngestionConfig.Connection],
)

object IngestionConfig {
  given Schema[IngestionConfig] = DeriveSchema.gen

  def default: IngestionConfig = IngestionConfig(Chunk.empty)

  case class Connection(
    id: IngestionConfig.Connection.Id,
    source: SourceDefinition.Id,
    sinks: NonEmptySet[SinkDefinition.Id],
  )

  object Connection {
    given Schema[IngestionConfig.Connection] = DeriveSchema.gen

    type Id = Id.Type
    object Id extends Newtype[Key] {
      import zio.schema.*
      def gen: Task[Id] = Key.gen.map(wrap)
      def apply(value: Long): Id = Id(Key(value))
      extension (a: Id) inline def asKey: Key = Id.unwrap(a)
      given Schema[Id] = derive
    }

  }

}

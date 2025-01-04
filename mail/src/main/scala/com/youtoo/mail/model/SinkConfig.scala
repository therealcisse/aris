package com.youtoo
package mail
package model

import zio.prelude.*

import com.youtoo.sink.model.*

case class SinkConfig(destinations: SinkConfig.Sinks)

object SinkConfig {
  import zio.schema.*

  given Schema[SinkConfig] = DeriveSchema.gen

  inline def empty: SinkConfig = SinkConfig(Sinks(Set()))

  type Sinks = Sinks.Type

  object Sinks extends Newtype[Set[SinkDefinition.Id]] {
    extension (a: Type) def value: Set[SinkDefinition.Id] = unwrap(a)
    given Schema[Type] = derive
  }

}

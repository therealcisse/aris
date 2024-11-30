package com.youtoo
package cqrs

case class EventProperty private (key: EventProperty.Key, value: String)

object EventProperty {
  import zio.*
  import zio.schema.*
  import zio.prelude.*

  given Schema[EventProperty] = DeriveSchema.gen

  type Key = Key.Type

  object Key extends Newtype[String] {
    import zio.schema.*

    extension (a: Key) inline def value: String = Key.unwrap(a)

    given Schema[Key] = derive
  }

  inline def apply(inline key: String, value: String): EventProperty = EventProperty(Key(key), value)
}

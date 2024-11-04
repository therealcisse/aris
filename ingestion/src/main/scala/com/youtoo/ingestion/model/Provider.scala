package com.youtoo
package ingestion
package model

import cats.implicits.*

import zio.*

import zio.prelude.*
import zio.schema.*

case class Provider(id: Provider.Id, name: Provider.Name, location: Provider.Location)

object Provider {
  given Schema[Provider] = DeriveSchema.gen

  type Id = Id.Type

  object Id extends Newtype[Key] {
    import zio.schema.*

    def gen: Task[Id] = Key.gen.map(wrap)

    def apply(value: Long): Id = Id(Key(value))

    extension (a: Id) inline def asKey: Key = Id.unwrap(a)

    given Schema[Id] = Schema
      .primitive[Long]
      .transform(
        Key.wrap `andThen` wrap,
        unwrap `andThen` Key.unwrap,
      )

  }

  type Name = Name.Type
  object Name extends Newtype[String] {
    import zio.schema.*

    extension (a: Name) inline def value: String = Name.unwrap(a)

    given Schema[Name] = Schema
      .primitive[String]
      .transform(
        wrap,
        unwrap,
      )

  }

  enum Location {
    case File(path: String)
  }

  object Location {
    given Schema[Location] = DeriveSchema.gen

  }

}

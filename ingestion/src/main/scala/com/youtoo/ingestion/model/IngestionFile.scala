package com.youtoo
package ingestion
package model

import cats.implicits.*

import zio.*

import zio.prelude.*
import zio.schema.*

case class IngestionFile(
  id: IngestionFile.Id,
  name: IngestionFile.Name,
  metadata: IngestionFile.Metadata,
  sig: IngestionFile.Sig,
)

object IngestionFile {
  given Schema[IngestionFile] = DeriveSchema.gen

  type Id = Id.Type

  object Id extends Newtype[Key] {
    import zio.schema.*

    def gen: Task[Id] = Key.gen.map(wrap)

    def apply(value: Long): Id = Id(Key(value))

    extension (a: Id) inline def asKey: Key = Id.unwrap(a)

    given Schema[Id] = derive

  }

  type Name = Name.Type
  object Name extends Newtype[String] {
    import zio.schema.*

    extension (a: Name) inline def value: String = Name.unwrap(a)

    given Schema[Name] = derive

  }

  type Sig = Sig.Type
  object Sig extends Newtype[String] {
    import zio.schema.*

    extension (a: Sig) inline def value: String = Sig.unwrap(a)

    given Schema[Sig] = derive

  }

  enum Metadata {
    case File(size: Long, lastModified: Timestamp)

  }

  object Metadata {
    given Schema[Metadata] = DeriveSchema.gen

  }

}

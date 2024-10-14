package com.youtoo.cqrs

import zio.*
import zio.prelude.*

type Discriminator = Discriminator.Type

object Discriminator extends Newtype[String] {
  import zio.schema.*

  extension (a: Discriminator) inline def value: String = Discriminator.unwrap(a)

  given Schema[Discriminator] = Schema
    .primitive[String]
    .transform(
      wrap,
      unwrap,
    )
}

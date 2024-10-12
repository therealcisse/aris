package com.youtoo.cqrs

import zio.prelude.*

type Timestamp = Timestamp.Type

object Timestamp extends Newtype[Long] {
  import zio.schema.*

  extension (a: Timestamp) inline def value: Long = Timestamp.unwrap(a)

  given Schema[Timestamp] = Schema
    .primitive[Long]
    .transform(
      wrap,
      unwrap,
    )
}

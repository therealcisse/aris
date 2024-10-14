package com.youtoo.cqrs

import zio.*
import zio.prelude.*

type Key = Key.Type

object Key extends Newtype[String] {
  import zio.schema.*

  def gen: Task[Key] = ZIO.attempt(Key(UlidGenerator.monotonic()))

  extension (a: Key) inline def value: String = Key.unwrap(a)

  given Schema[Key] = Schema
    .primitive[String]
    .transform(
      wrap,
      unwrap,
    )
}

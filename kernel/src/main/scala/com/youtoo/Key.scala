package com.youtoo

import zio.*
import zio.prelude.*

import cats.Order

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

  given Order[Key] = Order.by(_.value)
}

package com.youtoo

import zio.*
import zio.prelude.*

import cats.Order

type Key = Key.Type

object Key extends Newtype[Long] {
  import zio.schema.*

  def gen: Task[Key] = ZIO.attempt(Key(SnowflakeIdGenerator.INSTANCE.nextId()))

  extension (a: Key) inline def value: Long = Key.unwrap(a)

  given Schema[Key] = Schema
    .primitive[Long]
    .transform(
      wrap,
      unwrap,
    )

  given Order[Key] = Order.by(_.value)
}

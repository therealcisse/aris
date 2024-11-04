package com.youtoo
package cqrs

import zio.*
import zio.prelude.*

import cats.Order

type Version = Version.Type

object Version extends Newtype[Long] {
  import zio.schema.*

  def gen: Task[Version] = ZIO.attempt(Version(SnowflakeIdGenerator.INSTANCE.nextId()))

  extension (a: Version) inline def value: Long = Version.unwrap(a)
  extension (a: Version) inline def timestamp: Timestamp = Timestamp(SnowflakeIdGenerator.extractTimestamp(a.value))

  given Schema[Version] = Schema
    .primitive[Long]
    .transform(
      wrap,
      unwrap,
    )

  given Order[Version] = Order.by(_.value)
}

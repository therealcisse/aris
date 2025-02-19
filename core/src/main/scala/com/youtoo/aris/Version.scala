package com.youtoo
package aris

import zio.*
import zio.prelude.*

import cats.Order

type Version = Version.Type

object Version extends Newtype[Long] {
  import zio.schema.*

  def gen: Task[Version] = ZIO.attempt(Version(SnowflakeIdGenerator.INSTANCE.nextId()))

  extension (a: Version) inline def value: Long = Version.unwrap(a)
  extension (a: Version) inline def asKey: Key = Key.wrap(a.value)
  extension (a: Version) inline def timestamp: Timestamp = Timestamp(SnowflakeIdGenerator.extractTimestamp(a.value))

  given Schema[Version] = derive

  given Order[Version] = Order.by(_.value)
}

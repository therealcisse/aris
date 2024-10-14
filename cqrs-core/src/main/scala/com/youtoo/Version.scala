package com.youtoo.cqrs

import zio.*
import zio.prelude.*

import cats.Order

type Version = Version.Type

object Version extends Newtype[String] {
  import zio.schema.*

  def gen: Task[Version] = ZIO.attempt(Version(UlidGenerator.monotonic()))

  extension (a: Version) inline def value: String = Version.unwrap(a)

  given Schema[Version] = Schema
    .primitive[String]
    .transform(
      wrap,
      unwrap,
    )

  given Order[Version] = Order.by(_.value)
  given Ord[Version] = Ord.make((a, b) => Ord[String].compare(a.value, b.value))
}

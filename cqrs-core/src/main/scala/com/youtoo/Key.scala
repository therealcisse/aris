package com.youtoo.cqrs

import zio.*
import zio.prelude.*

import io.github.thibaultmeyer.cuid.CUID

type Key = Key.Type

object Key extends Newtype[CUID] {
  import zio.schema.*

  def gen: Task[Key] = ZIO.attempt(Key(CUID.randomCUID2(32)))

  inline def fromString(s: String): Task[Key] = ZIO.attempt(Key(CUID.fromString(s)))

  extension (a: Key) inline def value: CUID = Key.unwrap(a)

  given Schema[CUID] = Schema
    .primitive[String]
    .transform(
      CUID.fromString,
      _.toString,
    )

  given Schema[Key] = Schema[CUID].transform(
    wrap,
    unwrap,
  )
}

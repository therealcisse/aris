package com.youtoo.cqrs

import zio.prelude.*

type Version = Version.Type

object Version extends Newtype[Int] {
  import zio.schema.*

  extension (a: Version) inline def value: Int = Version.unwrap(a)

  given Schema[Version] = Schema
    .primitive[Int]
    .transform(
      wrap,
      unwrap,
    )
}

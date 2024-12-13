package com.youtoo

import zio.*
import zio.prelude.*

import cats.Order

type Lock = Lock.Type

object Lock extends Newtype[String] {
  import zio.schema.*

  extension (a: Lock) inline def value: String = Lock.unwrap(a)

  given Schema[Lock] = derive

  given Order[Lock] = Order.by(_.value)
}

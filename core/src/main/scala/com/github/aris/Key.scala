package com.github
package aris

import zio.*
import zio.prelude.*

import com.github.aris.service.*

import cats.Order

type Key = Key.Type

object Key extends Newtype[Long] {
  import zio.schema.*
  def gen: UIO[Key] = IdGenerator.gen().map(Key.wrap)
  extension (a: Key) inline def value: Long = Key.unwrap(a)
  given Schema[Key] = derive
  given Order[Key] = Order.by(_.value)
}

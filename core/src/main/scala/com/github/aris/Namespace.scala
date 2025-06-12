package com.github
package aris

import zio.*
import zio.prelude.*

type Namespace = Namespace.Type

object Namespace extends Newtype[Int] {
  import zio.schema.*

  extension (a: Namespace) inline def value: Int = Namespace.unwrap(a)
  inline def root: Namespace = Namespace.wrap(0)
  extension (a: Namespace) inline def asKey: Key = Key.wrap(a.value.toLong)

  given Schema[Namespace] = derive
}

package com.youtoo
package cqrs

import zio.*
import zio.prelude.*

type Namespace = Namespace.Type

object Namespace extends Newtype[Int] {
  import zio.schema.*

  extension (a: Namespace) inline def value: Int = Namespace.unwrap(a)

  given Schema[Namespace] = derive
}

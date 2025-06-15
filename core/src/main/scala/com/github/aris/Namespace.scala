package com.github
package aris

import zio.*
import zio.prelude.*

type Namespace = Namespace.Type

object Namespace extends Newtype[String] {
  import zio.schema.*

  extension (a: Namespace) inline def value: String = Namespace.unwrap(a)
  inline def root: Namespace = Namespace.wrap("root")

  given Schema[Namespace] = derive
}

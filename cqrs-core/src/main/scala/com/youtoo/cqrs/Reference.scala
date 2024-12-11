package com.youtoo
package cqrs

import zio.*
import zio.prelude.*

type Reference = Reference.Type

object Reference extends Newtype[Key] {
  import zio.schema.*

  extension (a: Reference) inline def value: Key = Reference.unwrap(a)

  def apply(value: Long): Reference = Reference(Key(value))

  extension (a: Reference) inline def asKey: Key = Reference.unwrap(a)

  given Schema[Reference] = derive
}

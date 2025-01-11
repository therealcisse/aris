package com.youtoo
package cqrs

import zio.*
import zio.prelude.*

type ReferenceKey = ReferenceKey.Type

object ReferenceKey extends Newtype[Key] {
  import zio.schema.*
  extension (a: ReferenceKey) inline def value: Key = ReferenceKey.unwrap(a)
  def apply(value: Long): ReferenceKey = ReferenceKey(Key(value))
  extension (a: ReferenceKey) inline def asKey: Key = ReferenceKey.unwrap(a)
  given Schema[ReferenceKey] = derive
}

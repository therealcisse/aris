package com.youtoo
package mail
package model

import zio.schema.*
import zio.prelude.*

type InternalDate = InternalDate.Type
object InternalDate extends Newtype[Timestamp] {
  extension (a: Type) def value: Timestamp = unwrap(a)
  given Schema[InternalDate] = derive

}


package com.youtoo
package mail
package model

import zio.*

import zio.schema.*
import zio.prelude.*

type TotalMessages = TotalMessages.Type

object TotalMessages extends Newtype[Int] {
  val empty: TotalMessages = TotalMessages(0)

  extension (a: Type) def value: Int = unwrap(a)

  extension (a: Type) def +(n: Int): Type = wrap(a.value + n)

  given Schema[TotalMessages] = derive

}

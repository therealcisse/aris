package com.youtoo
package mail
package model

import zio.schema.*
import zio.prelude.*

type MailToken = MailToken.Type
object MailToken extends Newtype[String] {
  extension (a: Type) def value: String = unwrap(a)
  given Schema[MailToken] = derive

}

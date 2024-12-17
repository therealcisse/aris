package com.youtoo
package mail
package model

import zio.*

import zio.prelude.*
import zio.schema.*

case class MailAccount(
  id: MailAccount.Id,
  name: MailAccount.Name,
  email: MailAccount.Email,
  settings: MailSettings,
  timestamp: Timestamp,
)

object MailAccount {
  type Id = Id.Type
  object Id extends Newtype[Key] {
    def gen: Task[Id] = Key.gen.map(wrap)
    def apply(value: Long): Id = Id(Key(value))
    extension (a: Id) def asKey: Key = unwrap(a)
    given Schema[Id] = derive
  }

  type Name = Name.Type
  object Name extends Newtype[String] {
    extension (a: Name) def value: String = unwrap(a)
    given Schema[Name] = derive
  }

  type Email = Email.Type
  object Email extends Newtype[String] {
    extension (a: Email) def value: String = unwrap(a)
    given Schema[Email] = derive
  }

  given Schema[MailAccount] = DeriveSchema.gen
}

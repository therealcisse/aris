package com.youtoo
package mail
package model

import zio.*

import zio.prelude.*
import zio.schema.*

import com.youtoo.lock.*

case class MailAccount(
  id: MailAccount.Id,
  accountType: AccountType,
  name: MailAccount.Name,
  email: MailAccount.Email,
  settings: MailConfig,
  timestamp: Timestamp,
) {

  inline def downloadLock: Lock = Lock("Download." ++ String.valueOf(id.asKey.value))
  inline def syncLock: Lock = Lock("Sync." ++ String.valueOf(id.asKey.value))
}

object MailAccount {
  type Id = Id.Type
  object Id extends Newtype[Key] {
    def gen: Task[Id] = Key.gen.map(wrap)
    def apply(value: Long): Id = Id(Key(value))
    extension (a: Type) def asKey: Key = unwrap(a)
    given Schema[Type] = derive
  }

  type Name = Name.Type
  object Name extends Newtype[String] {
    extension (a: Type) def value: String = unwrap(a)
    given Schema[Type] = derive
  }

  type Email = Email.Type
  object Email extends Newtype[String] {
    extension (a: Type) def value: String = unwrap(a)
    given Schema[Type] = derive
  }

  case class Information(
    accountType: AccountType,
    name: MailAccount.Name,
    email: MailAccount.Email,
    timestamp: Timestamp,
  )

  object Information {
    extension (info: Information)
      def toAccount(id: MailAccount.Id, config: MailConfig): MailAccount =
        MailAccount(id, info.accountType, info.name, info.email, config, info.timestamp)

  }

  given Schema[MailAccount] = DeriveSchema.gen
}

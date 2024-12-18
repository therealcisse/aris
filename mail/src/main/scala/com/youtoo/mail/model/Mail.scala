package com.youtoo
package mail
package model

import zio.*

import zio.schema.*

case class Mail(accountKey: MailAccount.Id, state: Mail.State)

object Mail {
  case class State()

  object State {
    given Schema[State] = DeriveSchema.gen

  }

  given Schema[Mail] = DeriveSchema.gen
}

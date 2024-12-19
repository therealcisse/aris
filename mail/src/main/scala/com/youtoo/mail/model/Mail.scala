package com.youtoo
package mail
package model

import zio.*

import zio.schema.*

case class Mail(accountKey: MailAccount.Id, token: MailToken)

object Mail {
  given Schema[Mail] = DeriveSchema.gen
}


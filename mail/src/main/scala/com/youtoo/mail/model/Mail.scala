package com.youtoo
package mail
package model

import zio.*

import zio.schema.*

case class Mail(accountKey: MailAccount.Id, cursor: Option[Cursor], authorization: Authorization)

object Mail {
  given Schema[Mail] = DeriveSchema.gen
}

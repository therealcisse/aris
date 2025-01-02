package com.youtoo
package mail
package model

import zio.schema.*
import com.youtoo.cqrs.Version

case class Download(accountKey: MailAccount.Id, lastVersion: Option[Version], authorization: Authorization)

object Download {
  given Schema[Download] = DeriveSchema.gen
}

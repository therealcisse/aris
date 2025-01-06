package com.youtoo
package mail
package input

import com.youtoo.mail.model.*

case class CreateGmailAccountRequest(
  name: MailAccount.Name,
  email: MailAccount.Email,
  authorizationCode: String,
)

object CreateGmailAccountRequest {
  import zio.schema.*

  given Schema[CreateGmailAccountRequest] = DeriveSchema.gen

}

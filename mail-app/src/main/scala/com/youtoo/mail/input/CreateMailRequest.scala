package com.youtoo
package mail
package input

import com.youtoo.mail.model.*

case class CreateMailAccountRequest(
  name: MailAccount.Name,
  email: MailAccount.Email,
  settings: MailSettings,
)

object CreateMailAccountRequest {
  import zio.schema.*

  given Schema[CreateMailAccountRequest] = DeriveSchema.gen

}

package com.youtoo
package mail
package input

import com.youtoo.mail.model.*

case class UpdateMailAccountRequest(
  id: MailAccount.Id,
  name: MailAccount.Name,
  email: MailAccount.Email,
  settings: MailSettings,
)

object UpdateMailAccountRequestRequest {
  import zio.schema.*

  given Schema[UpdateMailAccountRequest] = DeriveSchema.gen

}

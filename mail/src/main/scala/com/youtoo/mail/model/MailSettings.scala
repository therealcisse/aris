package com.youtoo
package mail
package model

import zio.schema.*

case class MailSettings(authConfig: AuthConfig, syncConfig: SyncConfig)

object MailSettings {
  given Schema[MailSettings] = DeriveSchema.gen
}

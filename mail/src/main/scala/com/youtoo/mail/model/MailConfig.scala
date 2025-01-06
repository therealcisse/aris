package com.youtoo
package mail
package model

import zio.schema.*

case class MailConfig(authConfig: AuthConfig, syncConfig: SyncConfig, sinkConfig: SinkConfig)

object MailConfig {
  given Schema[MailConfig] = DeriveSchema.gen

  def default: MailConfig = MailConfig(AuthConfig.default, SyncConfig.default, SinkConfig.empty)
}

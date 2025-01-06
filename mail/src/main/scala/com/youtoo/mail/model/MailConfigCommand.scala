package com.youtoo
package mail
package model

import zio.schema.{DeriveSchema, Schema}
import zio.prelude.*

import com.youtoo.cqrs.*

import com.youtoo.sink.model.*

enum MailConfigCommand {
  case EnableAutoSync(schedule: SyncConfig.CronExpression)
  case DisableAutoSync()
  case SetAuthConfig(config: AuthConfig)
  case LinkSink(sinkId: SinkDefinition.Id)
  case UnlinkSink(sinkId: SinkDefinition.Id)
}

object MailConfigCommand {
  given Schema[MailConfigCommand] = DeriveSchema.gen

  given CmdHandler[MailConfigCommand, MailConfigEvent] {
    def applyCmd(cmd: MailConfigCommand): NonEmptyList[MailConfigEvent] =
      cmd match {
        case MailConfigCommand.EnableAutoSync(schedule) =>
          NonEmptyList(MailConfigEvent.AutoSyncEnabled(schedule))
        case MailConfigCommand.DisableAutoSync() =>
          NonEmptyList(MailConfigEvent.AutoSyncDisabled(None))
        case MailConfigCommand.SetAuthConfig(config) =>
          NonEmptyList(MailConfigEvent.AuthConfigSet(config))
        case MailConfigCommand.LinkSink(sinkId) =>
          NonEmptyList(MailConfigEvent.SinkLinked(sinkId))
        case MailConfigCommand.UnlinkSink(sinkId) =>
          NonEmptyList(MailConfigEvent.SinkUnlinked(sinkId))
      }
  }
}

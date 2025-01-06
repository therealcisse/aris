package com.youtoo
package mail
package model

import cats.implicits.*

import zio.*

import zio.schema.{DeriveSchema, Schema}
import zio.prelude.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.sink.model.*

enum MailConfigEvent {
  case AutoSyncEnabled(schedule: SyncConfig.CronExpression)
  case AutoSyncDisabled(schedule: Option[SyncConfig.CronExpression])
  case AuthConfigSet(config: AuthConfig)
  case SinkLinked(sinkId: SinkDefinition.Id)
  case SinkUnlinked(sinkId: SinkDefinition.Id)
}

object MailConfigEvent {
  val discriminator: Discriminator = Discriminator("MailConfig")

  given Schema[MailConfigEvent] = DeriveSchema.gen

  given MetaInfo[MailConfigEvent] {
    extension (self: MailConfigEvent)
      def namespace: Namespace = self match {
        case MailConfigEvent.AutoSyncEnabled(_)    => NS.AutoSyncEnabled
        case MailConfigEvent.AutoSyncDisabled(_) => NS.AutoSyncDisabled
        case MailConfigEvent.AuthConfigSet(_) => NS.AuthConfigSet
        case MailConfigEvent.SinkLinked(_) => NS.SinkLinked
        case MailConfigEvent.SinkUnlinked(_) => NS.SinkUnlinked
      }

    extension (self: MailConfigEvent) def hierarchy = None
    extension (self: MailConfigEvent) def props = Chunk.empty
    extension (self: MailConfigEvent) def reference = None
  }

  object NS {
    val AutoSyncEnabled = Namespace(0)
    val AutoSyncDisabled = Namespace(100)
    val AuthConfigSet = Namespace(200)
    val SinkLinked = Namespace(300)
    val SinkUnlinked = Namespace(400)
  }

  private def updateMailConfig(event: MailConfigEvent)(state: Option[MailConfig]): Option[MailConfig] =
    (state, event) match {
      case (None, MailConfigEvent.AutoSyncEnabled(schedule)) => MailConfig(AuthConfig.default, SyncConfig(SyncConfig.AutoSync.enabled(schedule)), SinkConfig.empty).some
      case (Some(mc), MailConfigEvent.AutoSyncEnabled(schedule)) => mc.copy(syncConfig = mc.syncConfig.copy(autoSync = SyncConfig.AutoSync.enabled(schedule))).some
      case (None, MailConfigEvent.AutoSyncDisabled(schedule)) => MailConfig(AuthConfig.default, SyncConfig(SyncConfig.AutoSync.disabled(schedule)), SinkConfig.empty).some
      case (Some(mc), MailConfigEvent.AutoSyncDisabled(schedule)) => mc.copy(syncConfig = mc.syncConfig.copy(autoSync = SyncConfig.AutoSync.disabled(schedule))).some
      case (None, MailConfigEvent.AuthConfigSet(config)) => MailConfig(config, SyncConfig(SyncConfig.AutoSync.disabled(None)), SinkConfig.empty).some
      case (Some(mc), MailConfigEvent.AuthConfigSet(config)) => mc.copy(authConfig = config).some
      case (None, MailConfigEvent.SinkLinked(sinkId)) => MailConfig(AuthConfig.default, SyncConfig(SyncConfig.AutoSync.disabled(None)), SinkConfig(SinkConfig.Sinks(Set(sinkId)))).some
      case (Some(mc), MailConfigEvent.SinkLinked(sinkId)) => mc.copy(sinkConfig = mc.sinkConfig.copy(destinations = SinkConfig.Sinks(mc.sinkConfig.destinations.value + sinkId))).some
      case (None, MailConfigEvent.SinkUnlinked(sinkId)) => MailConfig(AuthConfig.default, SyncConfig(SyncConfig.AutoSync.disabled(None)), SinkConfig(SinkConfig.Sinks(Set(sinkId)))).some
      case (Some(mc), MailConfigEvent.SinkUnlinked(sinkId)) => mc.copy(sinkConfig = mc.sinkConfig.copy(destinations = SinkConfig.Sinks(mc.sinkConfig.destinations.value - sinkId))).some
    }

  class LoadMailConfig() extends EventHandler[MailConfigEvent, Option[MailConfig]] {
    def applyEvents(events: NonEmptyList[Change[MailConfigEvent]]): Option[MailConfig] =
      applyEvents(None, events)

    def applyEvents(zero: Option[MailConfig], events: NonEmptyList[Change[MailConfigEvent]]): Option[MailConfig] =
      events.foldLeft(zero) { (state, ch) =>
        updateMailConfig(ch.payload)(state)
      }

  }

}

package com.youtoo
package mail
package model

import com.youtoo.sink.*
import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*

import com.youtoo.cqrs.Codecs.given

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*

object MailConfigEventHandlerSpec extends ZIOSpecDefault {

  val spec = suite("MailConfigEventHandlerSpec")(
    test("LoadMailConfig should apply AutoSyncEnabled event") {
      check(cronExpressionGen) { cronExpression =>
        val events = NonEmptyList(Change(Version(1L), MailConfigEvent.AutoSyncEnabled(cronExpression)))
        val expected = MailConfig(
          AuthConfig.default,
          SyncConfig(SyncConfig.AutoSync.enabled(cronExpression)),
          SinkConfig.empty,
        )
        val result = EventHandler.applyEvents(events)(using MailConfigEvent.LoadMailConfig())
        assert(result)(isSome(equalTo(expected)))
      }
    },
    test("LoadMailConfig should apply AutoSyncDisabled event") {
      check(Gen.option(cronExpressionGen)) { cronExpression =>
        val events = NonEmptyList(Change(Version(1L), MailConfigEvent.AutoSyncDisabled(cronExpression)))
        val expected = MailConfig(
          AuthConfig.default,
          SyncConfig(SyncConfig.AutoSync.disabled(cronExpression)),
          SinkConfig.empty,
        )
        val result = EventHandler.applyEvents(events)(using MailConfigEvent.LoadMailConfig())
        assert(result)(isSome(equalTo(expected)))
      }
    },
    test("LoadMailConfig should apply AuthConfigSet event") {
      check(authConfigGen) { authConfig =>
        val events = NonEmptyList(Change(Version(1L), MailConfigEvent.AuthConfigSet(authConfig)))
        val expected = MailConfig(authConfig, SyncConfig(SyncConfig.AutoSync.disabled(None)), SinkConfig.empty)
        val result = EventHandler.applyEvents(events)(using MailConfigEvent.LoadMailConfig())
        assert(result)(isSome(equalTo(expected)))
      }
    },
    test("LoadMailConfig should apply SinkLinked event") {
      check(sinkIdGen) { sinkId =>
        val events = NonEmptyList(Change(Version(1L), MailConfigEvent.SinkLinked(sinkId)))
        val expected =
          MailConfig(
            AuthConfig.default,
            SyncConfig(SyncConfig.AutoSync.disabled(None)),
            SinkConfig(SinkConfig.Sinks(Set(sinkId))),
          )
        val result = EventHandler.applyEvents(events)(using MailConfigEvent.LoadMailConfig())
        assert(result)(isSome(equalTo(expected)))
      }
    },
    test("LoadMailConfig should apply SinkUnlinked event") {
      check(sinkIdGen) { sinkId =>
        val events = NonEmptyList(
          Change(Version(1L), MailConfigEvent.SinkLinked(sinkId)),
          Change(Version(2L), MailConfigEvent.SinkUnlinked(sinkId)),
        )
        val expected = MailConfig(AuthConfig(), SyncConfig(SyncConfig.AutoSync.disabled(None)), SinkConfig.empty)
        val result = EventHandler.applyEvents(events)(using MailConfigEvent.LoadMailConfig())
        assert(result)(isSome(equalTo(expected)))
      }
    },
    test("LoadMailConfig should apply multiple events in order") {
      check(cronExpressionGen, authConfigGen, sinkIdGen) { (cronExpression, authConfig, sinkId) =>
        val events = NonEmptyList(
          Change(Version(1L), MailConfigEvent.AutoSyncEnabled(cronExpression)),
          Change(Version(2L), MailConfigEvent.AuthConfigSet(authConfig)),
          Change(Version(3L), MailConfigEvent.SinkLinked(sinkId)),
        )
        val expected = MailConfig(
          authConfig,
          SyncConfig(SyncConfig.AutoSync.enabled(cronExpression)),
          SinkConfig(SinkConfig.Sinks(Set(sinkId))),
        )
        val result = EventHandler.applyEvents(events)(using MailConfigEvent.LoadMailConfig())
        assert(result)(isSome(equalTo(expected)))
      }
    },
  )
}

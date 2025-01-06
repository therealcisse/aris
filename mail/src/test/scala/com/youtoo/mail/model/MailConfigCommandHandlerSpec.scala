package com.youtoo
package mail
package model

import com.youtoo.cqrs.*
import com.youtoo.sink.*

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*

object MailConfigCommandHandlerSpec extends ZIOSpecDefault {
  val handler = summon[CmdHandler[MailConfigCommand, MailConfigEvent]]

  def spec = suite("MailConfigCommandHandlerSpec")(
    test("EnableAutoSync command produces AutoSyncEnabled event") {
      check(cronExpressionGen) { (cronExpression) =>
        val command = MailConfigCommand.EnableAutoSync(cronExpression)
        val events = handler.applyCmd(command)
        val expectedEvent = MailConfigEvent.AutoSyncEnabled(cronExpression)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("DisableAutoSync command produces AutoSyncDisabled event") {
      val command = MailConfigCommand.DisableAutoSync()
      val events = handler.applyCmd(command)
      val expectedEvent = MailConfigEvent.AutoSyncDisabled(None)
      assert(events)(equalTo(NonEmptyList(expectedEvent)))
    },
    test("SetAuthConfig command produces AuthConfigSet event") {
      check(authConfigGen) { (authConfig) =>
        val command = MailConfigCommand.SetAuthConfig(authConfig)
        val events = handler.applyCmd(command)
        val expectedEvent = MailConfigEvent.AuthConfigSet(authConfig)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("LinkSink command produces SinkLinked event") {
      check(sinkIdGen) { (sinkId) =>
        val command = MailConfigCommand.LinkSink(sinkId)
        val events = handler.applyCmd(command)
        val expectedEvent = MailConfigEvent.SinkLinked(sinkId)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("UnlinkSink command produces SinkUnlinked event") {
      check(sinkIdGen) { (sinkId) =>
        val command = MailConfigCommand.UnlinkSink(sinkId)
        val events = handler.applyCmd(command)
        val expectedEvent = MailConfigEvent.SinkUnlinked(sinkId)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
  )
}

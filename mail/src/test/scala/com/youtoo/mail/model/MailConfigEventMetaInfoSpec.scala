package com.youtoo
package mail
package model

import zio.test.*
import zio.test.Assertion.*

import com.youtoo.sink.*

object MailConfigEventMetaInfoSpec extends ZIOSpecDefault {

  val spec = suite("MailConfigEventMetaInfoSpec")(
    test("AutoSyncEnabled should have the correct namespace") {
      check(cronExpressionGen) { cronExpression =>
        val event = MailConfigEvent.AutoSyncEnabled(cronExpression)
        assert(event.namespace)(equalTo(MailConfigEvent.NS.AutoSyncEnabled))
      }
    },
    test("AutoSyncDisabled should have the correct namespace") {
      check(Gen.option(cronExpressionGen)) { cronExpression =>
        val event = MailConfigEvent.AutoSyncDisabled(cronExpression)
        assert(event.namespace)(equalTo(MailConfigEvent.NS.AutoSyncDisabled))
      }
    },
    test("AuthConfigSet should have the correct namespace") {
      check(authConfigGen) { authConfig =>
        val event = MailConfigEvent.AuthConfigSet(authConfig)
        assert(event.namespace)(equalTo(MailConfigEvent.NS.AuthConfigSet))
      }
    },
    test("SinkLinked should have the correct namespace") {
      check(sinkIdGen) { sinkId =>
        val event = MailConfigEvent.SinkLinked(sinkId)
        assert(event.namespace)(equalTo(MailConfigEvent.NS.SinkLinked))
      }
    },
    test("SinkUnlinked should have the correct namespace") {
      check(sinkIdGen) { sinkId =>
        val event = MailConfigEvent.SinkUnlinked(sinkId)
        assert(event.namespace)(equalTo(MailConfigEvent.NS.SinkUnlinked))
      }
    },
    test("All events should have an empty hierarchy") {
      check(mailConfigEventGen) { event =>
        assert(event.hierarchy)(isNone)
      }
    },
    test("All events should have empty props") {
      check(mailConfigEventGen) { event =>
        assert(event.props)(isEmpty)
      }
    },
    test("All events should have no reference") {
      check(mailConfigEventGen) { event =>
        assert(event.reference)(isNone)
      }
    },
  )
}

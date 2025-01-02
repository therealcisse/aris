package com.youtoo
package mail
package model

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*

import com.youtoo.cqrs.*

object AuthorizationCommandHandlerSpec extends ZIOSpecDefault {
  val handler = summon[CmdHandler[AuthorizationCommand, AuthorizationEvent]]

  def spec = suite("AuthorizationCommandHandlerSpec")(
    test("GrantAuthorization command produces AuthorizationGranted event") {
      check(tokenInfoGen, timestampGen) { (token, timestamp) =>
        val command = AuthorizationCommand.GrantAuthorization(token, timestamp)
        val events = handler.applyCmd(command)
        val expectedEvent = AuthorizationEvent.AuthorizationGranted(token = token, timestamp = timestamp)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("RevokeAuthorization command produces AuthorizationRevoked event") {
      check(timestampGen) { (timestamp) =>
        val command = AuthorizationCommand.RevokeAuthorization(timestamp)
        val events = handler.applyCmd(command)
        val expectedEvent = AuthorizationEvent.AuthorizationRevoked(timestamp = timestamp)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("Applying the same GrantAuthorization command multiple times produces the same event") {
      check(tokenInfoGen, timestampGen) { (token, timestamp) =>
        val command = AuthorizationCommand.GrantAuthorization(token, timestamp)
        val events1 = handler.applyCmd(command)
        val events2 = handler.applyCmd(command)
        assert(events1)(equalTo(events2))
      }
    },
    test("Applying the same RevokeAuthorization command multiple times produces the same event") {
      check(timestampGen) { (timestamp) =>
        val command = AuthorizationCommand.RevokeAuthorization(timestamp)
        val events1 = handler.applyCmd(command)
        val events2 = handler.applyCmd(command)
        assert(events1)(equalTo(events2))
      }
    },
  ) @@ TestAspect.withLiveClock
}

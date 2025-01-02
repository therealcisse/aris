package com.youtoo
package mail
package model

import com.youtoo.cqrs.Codecs.given

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*
import zio.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*

object AuthorizationEventHandlerSpec extends ZIOSpecDefault {
  def spec = suite("AuthorizationEventHandlerSpec")(
    test("Applying AuthorizationGranted event updates the state to Granted") {
      check(versionGen, tokenInfoGen, timestampGen) { (v1, tokenInfo, timestamp) =>
        val event = Change(v1, AuthorizationEvent.AuthorizationGranted(tokenInfo, timestamp))
        val state = EventHandler.applyEvents(NonEmptyList(event))(using AuthorizationEvent.LoadAuthorization())
        assert(state)(equalTo(Authorization.Granted(tokenInfo, timestamp)))
      }
    },
    test("Applying AuthorizationRevoked event updates the state to Revoked") {
      check(versionGen, timestampGen) { (v1, timestamp) =>
        val event = Change(v1, AuthorizationEvent.AuthorizationRevoked(timestamp))
        val state = EventHandler.applyEvents(NonEmptyList(event))(using AuthorizationEvent.LoadAuthorization())
        assert(state)(equalTo(Authorization.Revoked(timestamp)))
      }
    },
    test("Applying AuthorizationGranted then AuthorizationRevoked updates the state correctly") {
      check(versionGen, versionGen, tokenInfoGen, timestampGen, timestampGen) { (v1, v2, tokenInfo, ts1, ts2) =>
        val events = NonEmptyList(
          Change(v1, AuthorizationEvent.AuthorizationGranted(tokenInfo, ts1)),
          Change(v2, AuthorizationEvent.AuthorizationRevoked(ts2)),
        )
        val state = EventHandler.applyEvents(events)(using AuthorizationEvent.LoadAuthorization())
        assert(state)(equalTo(Authorization.Revoked(ts2)))
      }
    },
    test("Applying AuthorizationRevoked then AuthorizationGranted updates the state correctly") {
      check(versionGen, versionGen, tokenInfoGen, timestampGen, timestampGen) { (v1, v2, tokenInfo, ts1, ts2) =>
        val events = NonEmptyList(
          Change(v1, AuthorizationEvent.AuthorizationRevoked(ts1)),
          Change(v2, AuthorizationEvent.AuthorizationGranted(tokenInfo, ts2)),
        )
        val state = EventHandler.applyEvents(events)(using AuthorizationEvent.LoadAuthorization())
        assert(state)(equalTo(Authorization.Granted(tokenInfo, ts2)))
      }
    },
    test("Applying multiple AuthorizationGranted events keeps the latest grant") {
      check(versionGen, versionGen, versionGen, tokenInfoGen, tokenInfoGen, timestampGen, timestampGen) {
        (v1, v2, v3, tokenInfo1, tokenInfo2, ts1, ts2) =>
          val events = NonEmptyList(
            Change(v1, AuthorizationEvent.AuthorizationGranted(tokenInfo1, ts1)),
            Change(v2, AuthorizationEvent.AuthorizationGranted(tokenInfo2, ts2)),
          )
          val state = EventHandler.applyEvents(events)(using AuthorizationEvent.LoadAuthorization())
          assert(state)(equalTo(Authorization.Granted(tokenInfo2, ts2)))
      }
    },
  ) @@ TestAspect.withLiveClock
}

package com.youtoo
package mail
package model

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*

import com.youtoo.cqrs.domain.*

object LoadAuthorizationSpec extends ZIOSpecDefault {

  def spec = suite("LoadAuthorizationSpec")(
    test("should load Authorization.Granted from AuthorizationGranted event") {
      check(authorizationGrantedChangeGen) { change =>
        val handler = new MailEvent.LoadAuthorization()
        val result = handler.applyEvents(NonEmptyList(change))
        val payload = change.payload.asInstanceOf[MailEvent.AuthorizationGranted]
        assert(result)(equalTo(Authorization.Granted(payload.token, payload.timestamp)))
      }
    },
    test("should load Authorization.Revoked from AuthorizationRevoked event") {
      check(authorizationRevokedChangeGen) { change =>
        val handler = new MailEvent.LoadAuthorization()
        val result = handler.applyEvents(NonEmptyList(change))
        val payload = change.payload.asInstanceOf[MailEvent.AuthorizationRevoked]
        assert(result)(equalTo(Authorization.Revoked(payload.timestamp)))
      }
    },
    test("should load Authorization.Pending if no authorization events") {
      check(syncStartedChangeGen) { change =>
        val handler = new MailEvent.LoadAuthorization()
        val result = handler.applyEvents(NonEmptyList(change))
        assert(result)(equalTo(Authorization.Pending()))
      }
    },
    test("should correctly apply a sequence of authorization events") {
      check(
        Gen.oneOf(authorizationGrantedChangeGen, authorizationRevokedChangeGen),
        Gen.listOf(Gen.oneOf(authorizationGrantedChangeGen, authorizationRevokedChangeGen)),
      ) { (ch, changes) =>
        val handler = new MailEvent.LoadAuthorization()
        val nel = NonEmptyList(ch, changes*)
        val result = handler.applyEvents(nel)
        val lastEvent = nel.reverse.head.payload
        lastEvent match {
          case MailEvent.AuthorizationGranted(token, timestamp) =>
            assert(result)(equalTo(Authorization.Granted(token, timestamp)))
          case MailEvent.AuthorizationRevoked(timestamp) =>
            assert(result)(equalTo(Authorization.Revoked(timestamp)))
          case _ => assert(false)(isTrue) // Should not happen
        }
      }
    },
  )
}

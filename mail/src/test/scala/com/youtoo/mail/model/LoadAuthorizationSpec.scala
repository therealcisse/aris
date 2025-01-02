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
        val handler = new AuthorizationEvent.LoadAuthorization()
        val result = handler.applyEvents(NonEmptyList(change))
        val payload = change.payload.asInstanceOf[AuthorizationEvent.AuthorizationGranted]
        assert(result)(equalTo(Authorization.Granted(payload.token, payload.timestamp)))
      }
    },
    test("should load Authorization.Revoked from AuthorizationRevoked event") {
      check(authorizationRevokedChangeGen) { change =>
        val handler = new AuthorizationEvent.LoadAuthorization()
        val result = handler.applyEvents(NonEmptyList(change))
        val payload = change.payload.asInstanceOf[AuthorizationEvent.AuthorizationRevoked]
        assert(result)(equalTo(Authorization.Revoked(payload.timestamp)))
      }
    },
    test("should correctly apply a sequence of authorization events") {
      check(
        Gen.oneOf(authorizationGrantedChangeGen, authorizationRevokedChangeGen),
        Gen.listOf(Gen.oneOf(authorizationGrantedChangeGen, authorizationRevokedChangeGen)),
      ) { (ch, changes) =>
        val handler = new AuthorizationEvent.LoadAuthorization()
        val nel = NonEmptyList(ch, changes*)
        val result = handler.applyEvents(nel)
        val lastEvent = nel.reverse.head.payload
        lastEvent match {
          case AuthorizationEvent.AuthorizationGranted(token, timestamp) =>
            assert(result)(equalTo(Authorization.Granted(token, timestamp)))
          case AuthorizationEvent.AuthorizationRevoked(timestamp) =>
            assert(result)(equalTo(Authorization.Revoked(timestamp)))
          case _ => assert(result)(equalTo(Authorization.Pending()))
        }
      }
    },
  )
}

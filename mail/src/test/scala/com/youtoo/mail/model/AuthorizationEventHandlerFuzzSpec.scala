package com.youtoo
package mail
package model

import zio.*
import zio.test.*
import zio.prelude.*

import com.youtoo.cqrs.*

object AuthorizationEventHandlerFuzzSpec extends ZIOSpecDefault {

  def spec = suite("AuthorizationEventHandlerFuzzSpec")(
    test("Fuzz test AuthorizationEventHandler with random events") {
      check(authorizationEventChangeGen, Gen.listOf(authorizationEventChangeGen)) { (e, es) =>
        val events = NonEmptyList(e, es*)
        val result = ZIO.attempt(EventHandler.applyEvents(events)(using AuthorizationEvent.LoadAuthorization())).either
        result.map {
          case Left(_) =>
            throw IllegalStateException("fails")
          case Right(_) =>
            assertCompletes
        }
      }
    },
  ) @@ TestAspect.withLiveClock
}

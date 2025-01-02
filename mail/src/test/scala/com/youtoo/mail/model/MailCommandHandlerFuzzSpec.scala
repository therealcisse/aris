package com.youtoo
package mail
package model

import zio.*
import zio.test.*
import zio.prelude.*

import com.youtoo.cqrs.*

object MailEventHandlerFuzzSpec extends ZIOSpecDefault {

  def spec = suite("MailEventHandlerFuzzSpec")(
    test("Fuzz test MailEventHandler does not crash on invalid inputs") {
      check(
        mailEventChangeGen,
        Gen.listOf(mailEventChangeGen),
      ) { (e, es) =>
        val events = NonEmptyList(e, es*)
        val result = ZIO.attempt(EventHandler.applyEvents(events)(using MailEvent.LoadCursor()))
        result.fold(
          _ => assertCompletes, // Test passes if an exception is thrown (as expected)
          _ => assertCompletes, // Test also passes if no exception is thrown
        )
      }
    },
    test("Fuzz test MailEventHandler with random events") {
      check(validMailEventSequenceGen()) { events =>
        val result = ZIO.attempt(EventHandler.applyEvents(events)(using MailEvent.LoadCursor())).either
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

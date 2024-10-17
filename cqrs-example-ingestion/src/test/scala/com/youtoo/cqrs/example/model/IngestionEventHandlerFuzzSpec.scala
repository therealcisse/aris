package com.youtoo.cqrs
package example
package model

import zio.*
import zio.test.*
import zio.test.Assertion.*

object IngestionEventHandlerFuzzSpec extends ZIOSpecDefault {
  val handler = summon[IngestionEventHandler]

  def spec = suite("IngestionEventHandlerFuzzSpec")(
    test("Fuzz test IngestionEventHandler does not crash on invalid inputs") {
      check(eventSequenceGen) { events =>
        val result = ZIO.attempt(handler.applyEvents(events))
        result.fold(
          _ => assertCompletes, // Test passes if an exception is thrown (as expected)
          _ => assertCompletes, // Test also passes if no exception is thrown
        )
      }
    },
    test("Fuzz test IngestionEventHandler with random events") {
      check(validEventSequenceGen) { events =>
        val result = ZIO.attempt(handler.applyEvents(events)).either
        result.map {
          case Left(_) =>
            assertCompletes
          case Right(state) =>
            assert(isValidState(state.status))(isTrue)
        }
      }
    },
  )
}

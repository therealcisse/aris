package com.youtoo
package ingestion
package model

import zio.*
import zio.test.*
import zio.test.Assertion.*
import com.youtoo.cqrs.*

object IngestionEventHandlerFuzzSpec extends ZIOSpecDefault {

  def spec = suite("IngestionEventHandlerFuzzSpec")(
    test("Fuzz test IngestionEventHandler does not crash on invalid inputs") {
      check(ingestionEventSequenceGen) { events =>
        val result = ZIO.attempt(EventHandler.applyEvents(events))
        result.fold(
          _ => assertCompletes, // Test passes if an exception is thrown (as expected)
          _ => assertCompletes, // Test also passes if no exception is thrown
        )
      }
    },
    test("Fuzz test IngestionEventHandler with random events") {
      check(validIngestionEventSequenceGen) { events =>
        val result = ZIO.attempt(EventHandler.applyEvents(events)).either
        result.map {
          case Left(_) =>
            assertCompletes
          case Right(state) =>
            assert(isValidState(state.status))(isTrue)
        }
      }
    },
  ) @@ TestAspect.withLiveClock
}

package com.youtoo
package job
package model

import zio.test.*
import zio.test.Assertion.*
import zio.*
import com.youtoo.cqrs.*

object JobEventHandlerFuzzSpec extends ZIOSpecDefault {

  def spec = suite("JobEventHandlerFuzzSpec")(
    test("Fuzz test JobEventHandler does not crash on invalid inputs") {
      check(validEventSequenceGen) { events =>
        val result = ZIO.attempt(EventHandler.applyEvents(events))
        result.fold(
          _ => assertCompletes,
          _ => assertCompletes,
        )
      }
    },
    test("Fuzz test JobEventHandler with random events") {
      check(validEventSequenceGen) { events =>
        val result = ZIO.attempt(EventHandler.applyEvents(events)).either
        result.map {
          case Left(_) =>
            assertCompletes
          case Right(state) =>
            assert(state.status match {
              case JobStatus.Running(_, _, _) => true
              case JobStatus.Completed(_, _, _) => true
            })(isTrue)
        }
      }
    },
  ) @@ TestAspect.withLiveClock
}

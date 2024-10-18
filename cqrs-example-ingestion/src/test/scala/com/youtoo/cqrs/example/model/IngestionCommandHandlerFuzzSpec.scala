package com.youtoo.cqrs
package example
package model

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*
import zio.*

object IngestionCommandHandlerFuzzSpec extends ZIOSpecDefault {
  val handler = summon[IngestionCommandHandler]

  def spec = suite("IngestionCommandHandlerFuzzSpec")(
    test("Fuzz test IngestionCommandHandler with random commands") {
      check(Gen.listOf1(ingestionCommandGen)) { commands =>
        ZIO.foldLeft(commands)(assert(true)(isTrue)) { (s, cmd) =>
          ZIO.attempt(handler.applyCmd(cmd)).either.map {
            case Left(_) =>
              assert(false)(isTrue) // Fail the test if an exception is thrown
            case Right(events) =>
              s && assert(events.toList)(isNonEmpty)
          }
        }
      }
    },
  ) @@ TestAspect.samples(1)
}

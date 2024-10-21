package com.youtoo.cqrs
package migration
package model

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*
import zio.*

object MigrationCommandHandlerFuzzSpec extends ZIOSpecDefault {
  val handler = summon[MigrationCommandHandler]

  def spec = suite("MigrationCommandHandlerFuzzSpec")(
    test("Fuzz test MigrationCommandHandler with random commands") {
      check(Gen.listOf1(migrationCommandGen)) { commands =>
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

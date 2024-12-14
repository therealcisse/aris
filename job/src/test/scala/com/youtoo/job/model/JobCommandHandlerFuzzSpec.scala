package com.youtoo
package job
package model

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*
import zio.*
import com.youtoo.cqrs.*

object JobCommandHandlerFuzzSpec extends ZIOSpecDefault {

  def spec = suite("JobCommandHandlerFuzzSpec")(
    test("Fuzz test JobCommandHandler with random commands") {
      check(Gen.listOf1(jobCommandGen)) { commands =>
        ZIO.foldLeft(commands)(assert(true)(isTrue)) { (s, cmd) =>
          ZIO.attempt(CmdHandler.applyCmd(cmd)).either.map {
            case Left(_) =>
              assert(false)(isTrue) // Fail the test if an exception is thrown
            case Right(events) =>
              s && assert(events.toList)(isNonEmpty)
          }
        }
      }
    },
  )
}

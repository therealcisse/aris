package com.youtoo
package mail
package model

import zio.*
import zio.test.*

import com.youtoo.cqrs.*

object AuthorizationCommandHandlerFuzzSpec extends ZIOSpecDefault {

  def spec = suite("AuthorizationCommandHandlerFuzzSpec")(
    test("Fuzz test AuthorizationCommandHandler with random events") {
      check(authorizationCommandGen) { cmd =>
        val result = ZIO.attempt(CmdHandler.applyCmd(cmd)).either
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

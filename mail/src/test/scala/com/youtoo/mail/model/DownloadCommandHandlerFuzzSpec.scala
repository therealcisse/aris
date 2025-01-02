package com.youtoo
package mail
package model

import zio.*
import zio.test.*
import zio.prelude.*

import com.youtoo.cqrs.*

object DownloadCommandHandlerFuzzSpec extends ZIOSpecDefault {

  def spec = suite("DownloadCommandHandlerFuzzSpec")(
    test("Fuzz test DownloadCommandHandler with random cmds") {
      check(downloadCommandGen) { (cmd) =>
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

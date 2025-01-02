package com.youtoo
package mail
package model

import zio.*
import zio.test.*
import zio.prelude.*

import com.youtoo.cqrs.*

object DownloadEventHandlerFuzzSpec extends ZIOSpecDefault {

  def spec = suite("DownloadEventHandlerFuzzSpec")(
    test("Fuzz test DownloadEventHandler with random events") {
      check(downloadedChangeGen, Gen.listOf(downloadedChangeGen)) { (e, es) =>
        val events = NonEmptyList(e, es*)
        val result = ZIO.attempt(EventHandler.applyEvents(events)(using DownloadEvent.LoadVersion())).either
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

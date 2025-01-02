package com.youtoo
package mail
package model

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*

import com.youtoo.cqrs.*

object DownloadCommandHandlerSpec extends ZIOSpecDefault {
  val handler = summon[CmdHandler[DownloadCommand, DownloadEvent]]

  def spec = suite("DownloadCommandHandlerSpec")(
    test("RecordDownload command produces Downloaded event") {
      check(versionGen, jobIdGen) { (version, jobId) =>
        val command = DownloadCommand.RecordDownload(version, jobId)
        val events = handler.applyCmd(command)
        val expectedEvent = DownloadEvent.Downloaded(version = version, jobId = jobId)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("Applying the same RecordDownload command multiple times produces the same event") {
      check(versionGen, jobIdGen) { (version, jobId) =>
        val command = DownloadCommand.RecordDownload(version, jobId)
        val events1 = handler.applyCmd(command)
        val events2 = handler.applyCmd(command)
        assert(events1)(equalTo(events2))
      }
    },
  ) @@ TestAspect.withLiveClock
}

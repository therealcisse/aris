package com.youtoo
package mail
package model

import com.youtoo.cqrs.Codecs.given

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*
import zio.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*

object DownloadEventHandlerSpec extends ZIOSpecDefault {
  def spec = suite("DownloadEventHandlerSpec")(
    test("Applying Downloaded event updates the state") {
      check(versionGen, versionGen, jobIdGen, timestampGen) { (v1, v2, jobId, timestamp) =>
        val event = Change(v1, DownloadEvent.Downloaded(Version(0L), jobId))
        val state = EventHandler.applyEvents(NonEmptyList(event))(using DownloadEvent.LoadVersion())
        assert(state)(equalTo(Version(0L)))
      }
    },
    test("Applying multiple Downloaded events keeps the latest version") {
      check(versionGen, versionGen, versionGen, jobIdGen, jobIdGen, timestampGen, timestampGen) {
        (v1, v2, v3, jobId1, jobId2, ts1, ts2) =>
          val events = NonEmptyList(
            Change(v1, DownloadEvent.Downloaded(Version(0L), jobId1)),
            Change(v2, DownloadEvent.Downloaded(Version(1L), jobId2)),
          )
          val state = EventHandler.applyEvents(events)(using DownloadEvent.LoadVersion())
          assert(state)(equalTo(Version(1L)))
      }
    },
    test("Applying events out of order still results in the latest version") {
      check(versionGen, versionGen, versionGen, jobIdGen, jobIdGen, timestampGen, timestampGen) {
        (v1, v2, v3, jobId1, jobId2, ts1, ts2) =>
          val events = NonEmptyList(
            Change(v1, DownloadEvent.Downloaded(Version(0L), jobId1)),
            Change(v2, DownloadEvent.Downloaded(Version(1L), jobId2)),
          )
          val state = EventHandler.applyEvents(events)(using DownloadEvent.LoadVersion())
          assert(state)(equalTo(Version(1L)))
      }
    },
  ) @@ TestAspect.withLiveClock
}

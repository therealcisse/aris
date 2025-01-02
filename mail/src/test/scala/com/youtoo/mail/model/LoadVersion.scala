package com.youtoo
package mail
package model

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*

import com.youtoo.cqrs.domain.*

object LoadVersionSpec extends ZIOSpecDefault {

  def spec = suite("LoadVersionSpec")(
    test("should load the latest version from Downloaded events") {
      check(downloadedChangeGen, Gen.listOf(downloadedChangeGen)) { (e, es) =>
        val handler = new DownloadEvent.LoadVersion()
        val events = NonEmptyList(e, es*)
        val result = handler.applyEvents(events)
        val expectedVersion = events.reverse.head.payload.asInstanceOf[DownloadEvent.Downloaded].version
        assert(result)(equalTo(expectedVersion))
      }
    },
    test("should apply events to an initial state correctly") {
      check(downloadedChangeGen, Gen.listOf(downloadedChangeGen), versionGen) { (e, es, initialVersion) =>
        val handler = new DownloadEvent.LoadVersion()
        val events = NonEmptyList(e, es*)
        val result = handler.applyEvents(initialVersion, events)
        val expectedVersion = events.reverse.head.payload.asInstanceOf[DownloadEvent.Downloaded].version
        assert(result)(equalTo(expectedVersion))
      }
    },
  )
}

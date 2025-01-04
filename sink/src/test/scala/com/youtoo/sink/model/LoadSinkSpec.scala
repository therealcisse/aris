package com.youtoo
package sink
package model

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.*
import zio.prelude.*
import zio.test.Assertion.*
import zio.test.*

import com.youtoo.cqrs.Codecs.given

object LoadSinkSpec extends ZIOSpecDefault {

  def spec = suite("LoadSinkSpec")(
    test("should create a SinkDefinition when an Added event is applied") {
      check(sinkIdGen, sinkTypeGen, versionGen) { (sinkId, sinkType, version) =>
        val handler = new SinkEvent.LoadSink()
        val events = NonEmptyList(Change(version, SinkEvent.Added(sinkId, sinkType)))
        val result = handler.applyEvents(events)
        assert(result)(isSome(equalTo(SinkDefinition(sinkId, sinkType, created = version.timestamp, updated = None))))
      }
    },
    test("should delete a SinkDefinition when a Deleted event is applied after an Added event") {
      check(sinkIdGen, sinkTypeGen, versionGen, versionGen) { (sinkId, sinkType, addedVersion, deletedVersion) =>
        val handler = new SinkEvent.LoadSink()
        val events = NonEmptyList(
          Change(addedVersion, SinkEvent.Added(sinkId, sinkType)),
          Change(deletedVersion, SinkEvent.Deleted(sinkId)),
        )
        val result = handler.applyEvents(events)
        assert(result)(isNone)
      }
    },
    test("should update the SinkDefinition's updated timestamp when another Added event is applied") {
      check(sinkIdGen, sinkTypeGen, versionGen, versionGen) { (sinkId, sinkType, addedVersion1, addedVersion2) =>
        val handler = new SinkEvent.LoadSink()
        val events = NonEmptyList(
          Change(addedVersion1, SinkEvent.Added(sinkId, sinkType)),
          Change(addedVersion2, SinkEvent.Added(sinkId, sinkType)),
        )
        val result = handler.applyEvents(events)
        assert(result)(
          isSome(
            equalTo(
              SinkDefinition(
                sinkId,
                sinkType,
                created = addedVersion1.timestamp,
                updated = Some(addedVersion2.timestamp),
              ),
            ),
          ),
        )
      }
    },
  ) @@ TestAspect.withLiveClock
}

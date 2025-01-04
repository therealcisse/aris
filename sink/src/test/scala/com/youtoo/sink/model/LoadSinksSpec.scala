package com.youtoo
package sink
package model

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.*
import zio.prelude.*
import zio.test.Assertion.*
import zio.test.*

import com.youtoo.cqrs.Codecs.given

object LoadSinksSpec extends ZIOSpecDefault {

  def spec = suite("LoadSinksSpec")(
    test("should add a SinkDefinition to the map when an Added event is applied") {
      check(sinkIdGen, sinkTypeGen, versionGen) { (sinkId, sinkType, version) =>
        val handler = new SinkEvent.LoadSinks()
        val events = NonEmptyList(Change(version, SinkEvent.Added(sinkId, sinkType)))
        val result = handler.applyEvents(events)
        assert(result)(hasKey(sinkId)) && assert(result.values.toList)(
          contains(
            SinkDefinition(sinkId, sinkType, created = version.timestamp, updated = None),
          ),
        )
      }
    },
    test("should remove a SinkDefinition from the map when a Deleted event is applied") {
      check(sinkIdGen, sinkTypeGen, versionGen, versionGen) { (sinkId, sinkType, addedVersion, deletedVersion) =>
        val handler = new SinkEvent.LoadSinks()
        val events = NonEmptyList(
          Change(addedVersion, SinkEvent.Added(sinkId, sinkType)),
          Change(deletedVersion, SinkEvent.Deleted(sinkId)),
        )
        val result = handler.applyEvents(events)
        assert(result)(not(hasKey(sinkId)))
      }
    },
    test("should handle multiple Added events") {
      check(sinkIdGen, sinkIdGen, sinkTypeGen, sinkTypeGen, versionGen, versionGen) {
        (sinkId1, sinkId2, sinkType1, sinkType2, version1, version2) =>
          val handler = new SinkEvent.LoadSinks()
          val events = NonEmptyList(
            Change(version1, SinkEvent.Added(sinkId1, sinkType1)),
            Change(version2, SinkEvent.Added(sinkId2, sinkType2)),
          )
          val result = handler.applyEvents(events)
          assert(result)(
            hasKey(sinkId1) && hasKey(sinkId2),
          ) && assert(result.values.toList)(
            equalTo(
              SinkDefinition(sinkId1, sinkType1, created = version1.timestamp, updated = None) :: SinkDefinition(
                sinkId2,
                sinkType2,
                created = version2.timestamp,
                updated = None,
              ) :: Nil,
            ),
          )
      }
    },
    test("should not change the map if a Deleted event for a non-existent SinkDefinition is applied") {
      check(sinkIdGen, versionGen) { (sinkId, version) =>
        val handler = new SinkEvent.LoadSinks()
        val initialMap = Map(
          SinkDefinition.Id(1L) -> SinkDefinition(SinkDefinition.Id(1L), SinkType.InternalTable(), Timestamp(1L), None),
        )
        val events = NonEmptyList(Change(version, SinkEvent.Deleted(sinkId)))
        val result = handler.applyEvents(initialMap, events)
        assert(result)(equalTo(initialMap))
      }
    },
  )
}

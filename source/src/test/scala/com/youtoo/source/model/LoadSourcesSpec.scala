package com.youtoo
package source
package model

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.*
import zio.prelude.*
import zio.test.Assertion.*
import zio.test.*

import com.youtoo.cqrs.Codecs.given

object LoadSourcesSpec extends ZIOSpecDefault {

  def spec = suite("LoadSourcesSpec")(
    test("should add a SourceDefinition to the map when an Added event is applied") {
      check(sourceIdGen, sourceTypeGen, versionGen) { (sourceId, sourceType, version) =>
        val handler = new SourceEvent.LoadSources()
        val events = NonEmptyList(Change(version, SourceEvent.Added(sourceId, sourceType)))
        val result = handler.applyEvents(events)
        assert(result)(hasKey(sourceId)) && assert(result.values.toList)(
          contains(
            SourceDefinition(sourceId, sourceType, created = version.timestamp, updated = None),
          ),
        )
      }
    },
    test("should remove a SourceDefinition from the map when a Deleted event is applied") {
      check(sourceIdGen, sourceTypeGen, versionGen, versionGen) {
        (sourceId, sourceType, addedVersion, deletedVersion) =>
          val handler = new SourceEvent.LoadSources()
          val events = NonEmptyList(
            Change(addedVersion, SourceEvent.Added(sourceId, sourceType)),
            Change(deletedVersion, SourceEvent.Deleted(sourceId)),
          )
          val result = handler.applyEvents(events)
          assert(result)(not(hasKey(sourceId)))
      }
    },
    test("should handle multiple Added events") {
      check(sourceIdGen, sourceIdGen, sourceTypeGen, sourceTypeGen, versionGen, versionGen) {
        (sourceId1, sourceId2, sourceType1, sourceType2, version1, version2) =>
          val handler = new SourceEvent.LoadSources()
          val events = NonEmptyList(
            Change(version1, SourceEvent.Added(sourceId1, sourceType1)),
            Change(version2, SourceEvent.Added(sourceId2, sourceType2)),
          )
          val result = handler.applyEvents(events)
          assert(result)(
            hasKey(sourceId1) && hasKey(sourceId2),
          ) && assert(result.values.toList)(
            equalTo(
              SourceDefinition(
                sourceId1,
                sourceType1,
                created = version1.timestamp,
                updated = None,
              ) :: SourceDefinition(
                sourceId2,
                sourceType2,
                created = version2.timestamp,
                updated = None,
              ) :: Nil,
            ),
          )
      }
    },
    test("should not change the map if a Deleted event for a non-existent SourceDefinition is applied") {
      check(sourceIdGen, versionGen) { (sourceId, version) =>
        val handler = new SourceEvent.LoadSources()
        val initialMap = Map(
          SourceDefinition.Id(1L) -> SourceDefinition(
            SourceDefinition.Id(1L),
            SourceType.Grpc(SourceType.Info.GrpcInfo()),
            Timestamp(1L),
            None,
          ),
        )
        val events = NonEmptyList(Change(version, SourceEvent.Deleted(sourceId)))
        val result = handler.applyEvents(initialMap, events)
        assert(result)(equalTo(initialMap))
      }
    },
  )
}

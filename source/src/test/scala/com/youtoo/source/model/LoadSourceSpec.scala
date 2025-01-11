package com.youtoo
package source
package model

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.*
import zio.prelude.*
import zio.test.Assertion.*
import zio.test.*

import com.youtoo.cqrs.Codecs.given

object LoadSourceSpec extends ZIOSpecDefault {

  def spec = suite("LoadSourceSpec")(
    test("should create a SourceDefinition when an Added event is applied") {
      check(sourceIdGen, sourceTypeGen, versionGen) { (sourceId, sourceType, version) =>
        val handler = new SourceEvent.LoadSource()
        val events = NonEmptyList(Change(version, SourceEvent.Added(sourceId, sourceType)))
        val result = handler.applyEvents(events)
        assert(result)(
          isSome(equalTo(SourceDefinition(sourceId, sourceType, created = version.timestamp, updated = None))),
        )
      }
    },
    test("should delete a SourceDefinition when a Deleted event is applied after an Added event") {
      check(sourceIdGen, sourceTypeGen, versionGen, versionGen) {
        (sourceId, sourceType, addedVersion, deletedVersion) =>
          val handler = new SourceEvent.LoadSource()
          val events = NonEmptyList(
            Change(addedVersion, SourceEvent.Added(sourceId, sourceType)),
            Change(deletedVersion, SourceEvent.Deleted(sourceId)),
          )
          val result = handler.applyEvents(events)
          assert(result)(isNone)
      }
    },
    test("should update the SourceDefinition's updated timestamp when another Added event is applied") {
      check(sourceIdGen, sourceTypeGen, versionGen, versionGen) {
        (sourceId, sourceType, addedVersion1, addedVersion2) =>
          val handler = new SourceEvent.LoadSource()
          val events = NonEmptyList(
            Change(addedVersion1, SourceEvent.Added(sourceId, sourceType)),
            Change(addedVersion2, SourceEvent.Added(sourceId, sourceType)),
          )
          val result = handler.applyEvents(events)
          assert(result)(
            isSome(
              equalTo(
                SourceDefinition(
                  sourceId,
                  sourceType,
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

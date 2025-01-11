package com.youtoo
package source
package model

import com.youtoo.cqrs.Codecs.given

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*

object SourceEventHandlerSpec extends ZIOSpecDefault {
  given EventHandler[SourceEvent, Option[SourceDefinition]] = new SourceEvent.LoadSource()

  def spec = suite("SourceEventHandlerSpec")(
    test("Applying Added event initializes the state") {
      check(sourceIdGen, versionGen, sourceTypeGen) { (sourceId, v1, info) =>
        val event = Change(v1, SourceEvent.Added(sourceId, info))
        val state = EventHandler.applyEvents(NonEmptyList(event))

        val expectedState = SourceDefinition(
          sourceId,
          info,
          created = v1.timestamp,
          updated = None,
        )
        assert(state)(isSome(equalTo(expectedState)))
      }
    },
    test("Applying the same Added event multiple times should return the same state") {
      check(sourceIdGen, versionGen, sourceTypeGen) { (sourceId, v1, info) =>
        val event = Change(v1, SourceEvent.Added(sourceId, info))
        val state1 = EventHandler.applyEvents(NonEmptyList(event))
        val state2 = EventHandler.applyEvents(state1, NonEmptyList(event))
        assert(state1)(equalTo(state2.map(_.copy(updated = None))))
      }
    },
    test("Applying a Deleted event after an Added event should delete the source") {
      check(sourceIdGen, versionGen, versionGen, sourceTypeGen) { (sourceId, v1, v2, info) =>
        val events = NonEmptyList(
          Change(v1, SourceEvent.Added(sourceId, info)),
          Change(v2, SourceEvent.Deleted(sourceId)),
        )
        val state = EventHandler.applyEvents(events)
        assert(state)(equalTo(None))
      }
    },
    test("Applying a Added event after an Added event should reflect the source was updated") {
      check(sourceIdGen, versionGen, versionGen, sourceTypeGen) { (sourceId, v1, v2, info) =>
        val events = NonEmptyList(
          Change(v1, SourceEvent.Added(sourceId, info)),
          Change(v2, SourceEvent.Added(sourceId, info)),
        )

        val state = EventHandler.applyEvents(events)

        val expectedState = SourceDefinition(
          sourceId,
          info,
          created = v1.timestamp,
          updated = Some(v2.timestamp),
        )

        assert(state)(isSome(equalTo(expectedState)))
      }
    },
  ) @@ TestAspect.withLiveClock
}

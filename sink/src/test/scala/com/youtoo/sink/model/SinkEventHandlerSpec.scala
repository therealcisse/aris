package com.youtoo
package sink
package model

import com.youtoo.cqrs.Codecs.given

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*

object SinkEventHandlerSpec extends ZIOSpecDefault {
  given EventHandler[SinkEvent, Option[SinkDefinition]] = new SinkEvent.LoadSink()

  def spec = suite("SinkEventHandlerSpec")(
    test("Applying Added event initializes the state") {
      check(sinkIdGen, versionGen, sinkTypeGen) { (sinkId, v1, info) =>
        val event = Change(v1, SinkEvent.Added(sinkId, info))
        val state = EventHandler.applyEvents(NonEmptyList(event))

        val expectedState = SinkDefinition(
          sinkId,
          info,
          created = v1.timestamp,
          updated = None,
        )
        assert(state)(isSome(equalTo(expectedState)))
      }
    },
    test("Applying the same Added event multiple times should return the same state") {
      check(sinkIdGen, versionGen, sinkTypeGen) { (sinkId, v1, info) =>
        val event = Change(v1, SinkEvent.Added(sinkId, info))
        val state1 = EventHandler.applyEvents(NonEmptyList(event))
        val state2 = EventHandler.applyEvents(state1, NonEmptyList(event))
        assert(state1)(equalTo(state2.map(_.copy(updated = None))))
      }
    } @@ TestAspect.samples(1),
    test("Applying a Deleted event after an Added event should delete the sink") {
      check(sinkIdGen, versionGen, versionGen, sinkTypeGen) { (sinkId, v1, v2, info) =>
        val events = NonEmptyList(
          Change(v1, SinkEvent.Added(sinkId, info)),
          Change(v2, SinkEvent.Deleted(sinkId)),
        )
        val state = EventHandler.applyEvents(events)
        assert(state)(equalTo(None))
      }
    },
    test("Applying a Added event after an Added event should reflect the sink was updated") {
      check(sinkIdGen, versionGen, versionGen, sinkTypeGen) { (sinkId, v1, v2, info) =>
        val events = NonEmptyList(
          Change(v1, SinkEvent.Added(sinkId, info)),
          Change(v2, SinkEvent.Added(sinkId, info)),
        )

        val state = EventHandler.applyEvents(events)

        val expectedState = SinkDefinition(
          sinkId,
          info,
          created = v1.timestamp,
          updated = Some(v2.timestamp),
        )

        assert(state)(isSome(equalTo(expectedState)))
      }
    },
  ) @@ TestAspect.withLiveClock
}

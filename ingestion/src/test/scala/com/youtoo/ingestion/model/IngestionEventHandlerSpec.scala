package com.youtoo
package ingestion
package model

import com.youtoo.cqrs.Codecs.given

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*
import zio.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*

object IngestionEventHandlerSpec extends ZIOSpecDefault {
  def spec = suite("IngestionEventHandlerSpec")(
    test("Processing a file not in the resolved set does not alter state") {
      check(ingestionIdGen, versionGen, versionGen, versionGen, versionGen, timestampGen) {
        (ingestionId, v1, v2, v3, v4, timestamp) =>

          val events = NonEmptyList(
            Change(v1, IngestionEvent.IngestionStarted(ingestionId, timestamp)),
            Change(v2, IngestionEvent.IngestionFilesResolved(NonEmptySet(IngestionFile.Id(1L)))),
            Change(v3, IngestionEvent.IngestionFileProcessing(IngestionFile.Id(2L))), // file2 was not resolved
            Change(v4, IngestionEvent.IngestionFileProcessed(IngestionFile.Id(2L))), // file2 was not resolved
          )
          val state = EventHandler.applyEvents(events)
          val expectedStatus = Ingestion.Status.Resolved(
            files = NonEmptySet(IngestionFile.Id(1L)),
          )
          val expectedState = Ingestion(ingestionId, expectedStatus, timestamp)
          assert(state)(equalTo(expectedState))
      }
    },
    test("Applying IngestionStarted event initializes the state") {
      check(ingestionIdGen, versionGen, timestampGen) { (ingestionId, v1, timestamp) =>

        val event = Change(v1, IngestionEvent.IngestionStarted(ingestionId, timestamp))
        val state = EventHandler.applyEvents(NonEmptyList(event))
        val expectedState = Ingestion(ingestionId, Ingestion.Status.Initial(), timestamp)
        assert(state)(equalTo(expectedState))
      }

    },
    test("Applying multiple events updates the state correctly") {

      check(ingestionIdGen, versionGen, versionGen, versionGen, versionGen, versionGen, versionGen, timestampGen) {
        (ingestionId, v1, v2, v3, v4, v5, v6, timestamp) =>

          val events = NonEmptyList(
            Change(v1, IngestionEvent.IngestionStarted(ingestionId, timestamp)),
            Change(
              v2,
              IngestionEvent.IngestionFilesResolved(NonEmptySet(IngestionFile.Id(1L), IngestionFile.Id(2L))),
            ),
            Change(v3, IngestionEvent.IngestionFileProcessing(IngestionFile.Id(1L))),
            Change(v4, IngestionEvent.IngestionFileProcessing(IngestionFile.Id(2L))),
            Change(v5, IngestionEvent.IngestionFileProcessed(IngestionFile.Id(1L))),
            Change(v6, IngestionEvent.IngestionFileFailed(IngestionFile.Id(2L))),
          )
          val state = EventHandler.applyEvents(events)
          val expectedStatus = Ingestion.Status
            .Processing(
              remaining = Set.empty,
              processing = Set.empty,
              processed = Set(IngestionFile.Id(1L)),
              failed = Set(IngestionFile.Id(2L)),
            )
            .isSuccessful
          val expectedState = Ingestion(ingestionId, expectedStatus, timestamp)
          assert(state)(equalTo(expectedState))
      }

    },
    test("Applying the same event multiple times does not change the state") {
      check(ingestionIdGen, versionGen, versionGen, timestampGen) { (ingestionId, v1, v2, timestamp) =>

        val event = Change(v1, IngestionEvent.IngestionStarted(ingestionId, timestamp))
        val state1 = EventHandler.applyEvents(NonEmptyList(event))

        val ev1 = Change(
          v2,
          IngestionEvent.IngestionFilesResolved(NonEmptySet(IngestionFile.Id(1L), IngestionFile.Id(2L))),
        )
        val state2 = EventHandler.applyEvents(state1, NonEmptyList(ev1))
        val state3 = EventHandler.applyEvents(state2, NonEmptyList(ev1, ev1))
        assert(state2)(equalTo(state3))

      }

    },
    test("State transitions to Completed when all files are processed successfully") {
      check(ingestionIdGen, versionGen, versionGen, versionGen, versionGen, timestampGen) {
        (ingestionId, v1, v2, v3, v4, timestamp) =>

          val events = NonEmptyList(
            Change(v1, IngestionEvent.IngestionStarted(ingestionId, timestamp)),
            Change(v2, IngestionEvent.IngestionFilesResolved(NonEmptySet(IngestionFile.Id(1L)))),
            Change(v3, IngestionEvent.IngestionFileProcessing(IngestionFile.Id(1L))),
            Change(v4, IngestionEvent.IngestionFileProcessed(IngestionFile.Id(1L))),
          )
          val state = EventHandler.applyEvents(events)
          val expectedState = Ingestion(
            ingestionId,
            Ingestion.Status.Completed(NonEmptySet(IngestionFile.Id(1L))),
            timestamp,
          )
          assert(state)(equalTo(expectedState))
      }

    },
    test("State transitions to Failed when all files are processed with failures") {
      check(ingestionIdGen, versionGen, versionGen, versionGen, versionGen, versionGen, versionGen, timestampGen) {
        (ingestionId, v1, v2, v3, v4, v5, v6, timestamp) =>

          val events = NonEmptyList(
            Change(v1, IngestionEvent.IngestionStarted(ingestionId, timestamp)),
            Change(
              v2,
              IngestionEvent.IngestionFilesResolved(NonEmptySet(IngestionFile.Id(1L), IngestionFile.Id(2L))),
            ),
            Change(v3, IngestionEvent.IngestionFileProcessing(IngestionFile.Id(1L))),
            Change(v4, IngestionEvent.IngestionFileProcessing(IngestionFile.Id(2L))),
            Change(v5, IngestionEvent.IngestionFileFailed(IngestionFile.Id(1L))),
            Change(v6, IngestionEvent.IngestionFileFailed(IngestionFile.Id(2L))),
          )
          val state = EventHandler.applyEvents(events)
          val expectedState = Ingestion(
            ingestionId,
            Ingestion.Status.Failed(Set.empty, NonEmptySet(IngestionFile.Id(1L), IngestionFile.Id(2L))),
            timestamp,
          )
          assert(state)(equalTo(expectedState))
      }

    },
    test("Applying events out of order throws an exception") {
      check(ingestionIdGen, versionGen, versionGen, timestampGen) { (ingestionId, v1, v2, timestamp) =>

        val events = NonEmptyList(
          Change(
            v1,
            IngestionEvent.IngestionFilesResolved(NonEmptySet(IngestionFile.Id(1L), IngestionFile.Id(2L))),
          ),
          Change(v2, IngestionEvent.IngestionStarted(ingestionId, timestamp)),
        )
        assertZIO(ZIO.attempt(EventHandler.applyEvents(events)).exit)(failsWithA[IllegalArgumentException])
      }

    },
  ) @@ TestAspect.withLiveClock
}

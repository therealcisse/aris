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
  val ingestionId = Ingestion.Id(Key("ingestion-1"))

  def spec = suite("IngestionEventHandlerSpec")(
    test("Processing a file not in the resolved set does not alter state") {
      check(versionGen, versionGen, versionGen, versionGen, timestampGen) { (v1, v2, v3, v4, timestamp) =>

        val events = NonEmptyList(
          Change(v1, IngestionEvent.IngestionStarted(ingestionId, timestamp)),
          Change(v2, IngestionEvent.IngestionFilesResolved(NonEmptySet(IngestionFile.Id("file1")))),
          Change(v3, IngestionEvent.IngestionFileProcessing(IngestionFile.Id("file2"))), // file2 was not resolved
          Change(v4, IngestionEvent.IngestionFileProcessed(IngestionFile.Id("file2"))), // file2 was not resolved
        )
        val state = EventHandler.applyEvents(events)
        val expectedStatus = Ingestion.Status.Resolved(
          files = NonEmptySet(IngestionFile.Id("file1")),
        )
        val expectedState = Ingestion(ingestionId, expectedStatus, timestamp)
        assert(state)(equalTo(expectedState))
      }
    },
    test("Applying IngestionStarted event initializes the state") {
      check(versionGen, timestampGen) { (v1, timestamp) =>

        val event = Change(v1, IngestionEvent.IngestionStarted(ingestionId, timestamp))
        val state = EventHandler.applyEvents(NonEmptyList(event))
        val expectedState = Ingestion(ingestionId, Ingestion.Status.Initial(), timestamp)
        assert(state)(equalTo(expectedState))
      }

    },
    test("Applying multiple events updates the state correctly") {

      check(versionGen, versionGen, versionGen, versionGen, versionGen, versionGen, timestampGen) {
        (v1, v2, v3, v4, v5, v6, timestamp) =>

          val events = NonEmptyList(
            Change(v1, IngestionEvent.IngestionStarted(ingestionId, timestamp)),
            Change(
              v2,
              IngestionEvent.IngestionFilesResolved(NonEmptySet(IngestionFile.Id("file1"), IngestionFile.Id("file2"))),
            ),
            Change(v3, IngestionEvent.IngestionFileProcessing(IngestionFile.Id("file1"))),
            Change(v4, IngestionEvent.IngestionFileProcessing(IngestionFile.Id("file2"))),
            Change(v5, IngestionEvent.IngestionFileProcessed(IngestionFile.Id("file1"))),
            Change(v6, IngestionEvent.IngestionFileFailed(IngestionFile.Id("file2"))),
          )
          val state = EventHandler.applyEvents(events)
          val expectedStatus = Ingestion.Status
            .Processing(
              remaining = Set.empty,
              processing = Set.empty,
              processed = Set(IngestionFile.Id("file1")),
              failed = Set(IngestionFile.Id("file2")),
            )
            .isSuccessful
          val expectedState = Ingestion(ingestionId, expectedStatus, timestamp)
          assert(state)(equalTo(expectedState))
      }

    },
    test("Applying the same event multiple times does not change the state") {
      check(versionGen, versionGen, timestampGen) { (v1, v2, timestamp) =>

        val event = Change(v1, IngestionEvent.IngestionStarted(ingestionId, timestamp))
        val state1 = EventHandler.applyEvents(NonEmptyList(event))

        val ev1 = Change(
          v2,
          IngestionEvent.IngestionFilesResolved(NonEmptySet(IngestionFile.Id("file1"), IngestionFile.Id("file2"))),
        )
        val state2 = EventHandler.applyEvents(state1, NonEmptyList(ev1))
        val state3 = EventHandler.applyEvents(state2, NonEmptyList(ev1, ev1))
        assert(state2)(equalTo(state3))

      }

    },
    test("State transitions to Completed when all files are processed successfully") {
      check(versionGen, versionGen, versionGen, versionGen, timestampGen) { (v1, v2, v3, v4, timestamp) =>

        val events = NonEmptyList(
          Change(v1, IngestionEvent.IngestionStarted(ingestionId, timestamp)),
          Change(v2, IngestionEvent.IngestionFilesResolved(NonEmptySet(IngestionFile.Id("file1")))),
          Change(v3, IngestionEvent.IngestionFileProcessing(IngestionFile.Id("file1"))),
          Change(v4, IngestionEvent.IngestionFileProcessed(IngestionFile.Id("file1"))),
        )
        val state = EventHandler.applyEvents(events)
        val expectedState = Ingestion(
          ingestionId,
          Ingestion.Status.Completed(NonEmptySet(IngestionFile.Id("file1"))),
          timestamp,
        )
        assert(state)(equalTo(expectedState))
      }

    },
    test("State transitions to Failed when all files are processed with failures") {
      check(versionGen, versionGen, versionGen, versionGen, versionGen, versionGen, timestampGen) {
        (v1, v2, v3, v4, v5, v6, timestamp) =>

          val events = NonEmptyList(
            Change(v1, IngestionEvent.IngestionStarted(ingestionId, timestamp)),
            Change(
              v2,
              IngestionEvent.IngestionFilesResolved(NonEmptySet(IngestionFile.Id("file1"), IngestionFile.Id("file2"))),
            ),
            Change(v3, IngestionEvent.IngestionFileProcessing(IngestionFile.Id("file1"))),
            Change(v4, IngestionEvent.IngestionFileProcessing(IngestionFile.Id("file2"))),
            Change(v5, IngestionEvent.IngestionFileFailed(IngestionFile.Id("file1"))),
            Change(v6, IngestionEvent.IngestionFileFailed(IngestionFile.Id("file2"))),
          )
          val state = EventHandler.applyEvents(events)
          val expectedState = Ingestion(
            ingestionId,
            Ingestion.Status.Failed(Set.empty, NonEmptySet(IngestionFile.Id("file1"), IngestionFile.Id("file2"))),
            timestamp,
          )
          assert(state)(equalTo(expectedState))
      }

    },
    test("Applying events out of order throws an exception") {
      check(versionGen, versionGen, timestampGen) { (v1, v2, timestamp) =>

        val events = NonEmptyList(
          Change(
            v1,
            IngestionEvent.IngestionFilesResolved(NonEmptySet(IngestionFile.Id("file1"), IngestionFile.Id("file2"))),
          ),
          Change(v2, IngestionEvent.IngestionStarted(ingestionId, timestamp)),
        )
        assertZIO(ZIO.attempt(EventHandler.applyEvents(events)).exit)(failsWithA[IllegalArgumentException])
      }

    },
  ) @@ TestAspect.withLiveClock
}

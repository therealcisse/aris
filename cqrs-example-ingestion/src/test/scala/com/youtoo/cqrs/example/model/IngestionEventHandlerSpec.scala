package com.youtoo.cqrs
package example
package model

import com.youtoo.cqrs.Codecs.given

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*
import zio.*

import com.youtoo.cqrs.domain.*

object IngestionEventHandlerSpec extends ZIOSpecDefault {
  val handler = summon[IngestionEventHandler]
  val ingestionId = Ingestion.Id(Key("ingestion-1"))

  def spec = suite("IngestionEventHandlerSpec")(
    test("Processing a file not in the resolved set does not alter state") {
      (Version.gen <*> Version.gen <*> Version.gen <*> Version.gen <*> Timestamp.now) map { (v1, v2, v3, v4, timestamp) =>

        val events = NonEmptyList(
          Change(v1, IngestionEvent.IngestionStarted(ingestionId, timestamp)),
          Change(v2, IngestionEvent.IngestionFilesResolved(NonEmptySet("file1"))),
          Change(v3, IngestionEvent.IngestionFileProcessing("file2")), // file2 was not resolved
          Change(v4, IngestionEvent.IngestionFileProcessed("file2")), // file2 was not resolved
        )
        val state = handler.applyEvents(events)
        val expectedStatus = Ingestion.Status.Resolved(
          files = NonEmptySet("file1"),
        )
        val expectedState = Ingestion(ingestionId, expectedStatus, timestamp)
        assert(state)(equalTo(expectedState))
      }
    },
    test("Applying IngestionStarted event initializes the state") {
      (Version.gen <*> Timestamp.now) map { (v1, timestamp) =>

        val event = Change(v1, IngestionEvent.IngestionStarted(ingestionId, timestamp))
        val state = handler.applyEvents(NonEmptyList(event))
        val expectedState = Ingestion(ingestionId, Ingestion.Status.Initial(), timestamp)
        assert(state)(equalTo(expectedState))
      }

    },
    test("Applying multiple events updates the state correctly") {

      (Version.gen <*> Version.gen <*> Version.gen <*> Version.gen <*> Version.gen <*> Version.gen <*> Timestamp.now) map {
        (v1, v2, v3, v4, v5, v6, timestamp) =>

          val events = NonEmptyList(
            Change(v1, IngestionEvent.IngestionStarted(ingestionId, timestamp)),
            Change(v2, IngestionEvent.IngestionFilesResolved(NonEmptySet("file1", "file2"))),
            Change(v3, IngestionEvent.IngestionFileProcessing("file1")),
            Change(v4, IngestionEvent.IngestionFileProcessing("file2")),
            Change(v5, IngestionEvent.IngestionFileProcessed("file1")),
            Change(v6, IngestionEvent.IngestionFileFailed("file2")),
          )
          val state = handler.applyEvents(events)
          val expectedStatus = Ingestion.Status
            .Processing(
              remaining = Set.empty,
              processing = Set.empty,
              processed = Set("file1"),
              failed = Set("file2"),
            )
            .isSuccessful
          val expectedState = Ingestion(ingestionId, expectedStatus, timestamp)
          assert(state)(equalTo(expectedState))
      }

    },
    test("Applying the same event multiple times does not change the state") {
      (Version.gen <*> Version.gen <*> Timestamp.now) map { (v1, v2, timestamp) =>

        val event = Change(v1, IngestionEvent.IngestionStarted(ingestionId, timestamp))
        val state1 = handler.applyEvents(NonEmptyList(event))

        val ev1 = Change(v2, IngestionEvent.IngestionFilesResolved(NonEmptySet("file1", "file2")))
        val state2 = handler.applyEvents(state1, NonEmptyList(ev1))
        val state3 = handler.applyEvents(state2, NonEmptyList(ev1, ev1))
        assert(state2)(equalTo(state3))

      }

    },
    test("State transitions to Completed when all files are processed successfully") {
      (Version.gen <*> Version.gen <*> Version.gen <*> Version.gen <*> Timestamp.now) map { (v1, v2, v3, v4, timestamp) =>

        val events = NonEmptyList(
          Change(v1, IngestionEvent.IngestionStarted(ingestionId, timestamp)),
          Change(v2, IngestionEvent.IngestionFilesResolved(NonEmptySet("file1"))),
          Change(v3, IngestionEvent.IngestionFileProcessing("file1")),
          Change(v4, IngestionEvent.IngestionFileProcessed("file1")),
        )
        val state = handler.applyEvents(events)
        val expectedState = Ingestion(
          ingestionId,
          Ingestion.Status.Completed(NonEmptySet("file1")),
          timestamp,
        )
        assert(state)(equalTo(expectedState))
      }

    },
    test("State transitions to Failed when all files are processed with failures") {
      (Version.gen <*> Version.gen <*> Version.gen <*> Version.gen <*> Version.gen <*> Version.gen <*> Timestamp.now) map {
        (v1, v2, v3, v4, v5, v6, timestamp) =>

          val events = NonEmptyList(
            Change(v1, IngestionEvent.IngestionStarted(ingestionId, timestamp)),
            Change(v2, IngestionEvent.IngestionFilesResolved(NonEmptySet("file1", "file2"))),
            Change(v3, IngestionEvent.IngestionFileProcessing("file1")),
            Change(v4, IngestionEvent.IngestionFileProcessing("file2")),
            Change(v5, IngestionEvent.IngestionFileFailed("file1")),
            Change(v6, IngestionEvent.IngestionFileFailed("file2")),
          )
          val state = handler.applyEvents(events)
          val expectedState = Ingestion(
            ingestionId,
            Ingestion.Status.Failed(Set.empty, NonEmptySet("file1", "file2")),
            timestamp,
          )
          assert(state)(equalTo(expectedState))
      }

    },
    test("Applying events out of order throws an exception") {
      (Version.gen <*> Version.gen <*> Timestamp.now) flatMap { (v1, v2, timestamp) =>

        val events = NonEmptyList(
          Change(v1, IngestionEvent.IngestionFilesResolved(NonEmptySet("file1", "file2"))),
          Change(v2, IngestionEvent.IngestionStarted(ingestionId, timestamp)),
        )
        assertZIO(ZIO.attempt(handler.applyEvents(events)).exit)(failsWithA[IllegalArgumentException])
      }

    },
  )
}

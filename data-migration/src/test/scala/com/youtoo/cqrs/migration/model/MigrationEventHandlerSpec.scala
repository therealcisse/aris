package com.youtoo.cqrs
package migration
package model

import com.youtoo.cqrs.Codecs.given

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*
import zio.*
import com.youtoo.cqrs.domain.*

object MigrationEventHandlerSpec extends ZIOSpecDefault {
  val handler = summon[MigrationEventHandler]

  def spec = suite("MigrationEventHandlerSpec")(
    test("Applying MigrationRegistered event initializes the state") {
      check(migrationIdGen, timestampGen, versionGen) { (id, timestamp, version) =>
        val event = Change(version, MigrationEvent.MigrationRegistered(id, timestamp))
        val state = handler.applyEvents(NonEmptyList(event))
        val expectedState = Migration(id, Migration.State(Map.empty), timestamp)
        assert(state)(equalTo(expectedState)) && assert(state.state.status)(equalTo(ExecutionStatus.registered))
      }
    },
    test("Applying ExecutionStarted event adds a new execution") {
      check(migrationIdGen, executionIdGen, timestampGen, versionGen, versionGen) {
        (migrationId, executionId, timestamp, v1, v2) =>
          val events = NonEmptyList(
            Change(v1, MigrationEvent.MigrationRegistered(migrationId, timestamp)),
            Change(v2, MigrationEvent.ExecutionStarted(executionId, timestamp)),
          )
          val state = handler.applyEvents(events)
          val execution = Execution.Processing(executionId, Stats.empty, timestamp)
          val expectedState = Migration(
            migrationId,
            Migration.State(Map(executionId -> execution)),
            timestamp,
          )
          assert(state)(equalTo(expectedState)) && assert(state.state.status)(equalTo(ExecutionStatus.running))
      }
    },
    test("ProcessingStarted event updates the execution with processing key") {
      check(migrationIdGen, executionIdGen, keyGen, timestampGen, versionGen, versionGen, versionGen) {
        (migrationId, executionId, key, timestamp, v1, v2, v3) =>
          val events = NonEmptyList(
            Change(v1, MigrationEvent.MigrationRegistered(migrationId, timestamp)),
            Change(v2, MigrationEvent.ExecutionStarted(executionId, timestamp)),
            Change(v3, MigrationEvent.ProcessingStarted(executionId, key)),
          )
          val state = handler.applyEvents(events)
          val stats = Stats(processing = Set(key), processed = Set.empty, failed = Set.empty)
          val processingExecution: Execution.Processing = Execution.Processing(executionId, stats, timestamp)
          val expectedState = Migration(
            migrationId,
            Migration.State(Map(executionId -> processingExecution)),
            timestamp,
          )
          assert(state)(equalTo(expectedState)) && assert(state.state.status)(equalTo(ExecutionStatus.running))
      }
    },
    test("KeyProcessed event moves key from processing to processed") {
      check(migrationIdGen, executionIdGen, keyGen, timestampGen, versionGen, versionGen, versionGen, versionGen) {
        (migrationId, executionId, key, timestamp, v1, v2, v3, v4) =>
          val events = NonEmptyList(
            Change(v1, MigrationEvent.MigrationRegistered(migrationId, timestamp)),
            Change(v2, MigrationEvent.ExecutionStarted(executionId, timestamp)),
            Change(v3, MigrationEvent.ProcessingStarted(executionId, key)),
            Change(v4, MigrationEvent.KeyProcessed(executionId, key)),
          )
          val state = handler.applyEvents(events)
          val stats = Stats(processing = Set.empty, processed = Set(key), failed = Set.empty)
          val processingExecution: Execution.Processing = Execution.Processing(executionId, stats, timestamp)
          val expectedState = Migration(
            migrationId,
            Migration.State(Map(executionId -> processingExecution)),
            timestamp,
          )
          assert(state)(equalTo(expectedState)) && assert(state.state.status)(equalTo(ExecutionStatus.running))
      }
    },
    test("ProcessingFailed event moves key from processing to failed") {
      check(migrationIdGen, executionIdGen, keyGen, timestampGen, versionGen, versionGen, versionGen, versionGen) {
        (migrationId, executionId, key, timestamp, v1, v2, v3, v4) =>
          val events = NonEmptyList(
            Change(v1, MigrationEvent.MigrationRegistered(migrationId, timestamp)),
            Change(v2, MigrationEvent.ExecutionStarted(executionId, timestamp)),
            Change(v3, MigrationEvent.ProcessingStarted(executionId, key)),
            Change(v4, MigrationEvent.ProcessingFailed(executionId, key)),
          )
          val state = handler.applyEvents(events)
          val stats = Stats(processing = Set.empty, processed = Set.empty, failed = Set(key))
          val processingExecution: Execution.Processing = Execution.Processing(executionId, stats, timestamp)
          val expectedState = Migration(
            migrationId,
            Migration.State(Map(executionId -> processingExecution)),
            timestamp,
          )
          assert(state)(equalTo(expectedState)) && assert(state.state.status)(equalTo(ExecutionStatus.running))
      }
    },
    test("ExecutionStopped transitions execution to Stopped state") {
      check(migrationIdGen, executionIdGen, timestampGen, versionGen, versionGen, versionGen) {
        (migrationId, executionId, timestamp, v1, v2, v3) =>
          val events = NonEmptyList(
            Change(v1, MigrationEvent.MigrationRegistered(migrationId, timestamp)),
            Change(v2, MigrationEvent.ExecutionStarted(executionId, timestamp)),
            Change(v3, MigrationEvent.ExecutionStopped(executionId, timestamp)),
          )
          val processing: Execution.Processing =
            Execution.Processing(executionId, Stats(Set.empty, Set.empty, Set.empty), timestamp)
          val stoppedExecution = Execution.Stopped(processing, timestamp)
          val state = handler.applyEvents(events)
          val expectedState = Migration(
            migrationId,
            Migration.State(Map(executionId -> stoppedExecution)),
            timestamp,
          )
          assert(state)(equalTo(expectedState)) && assert(state.state.status)(equalTo(ExecutionStatus.stopped))
      }
    },
    test("ExecutionFinished transitions execution to Finished state") {
      check(migrationIdGen, executionIdGen, timestampGen, versionGen, versionGen, versionGen) {
        (migrationId, executionId, timestamp, v1, v2, v3) =>
          val events = NonEmptyList(
            Change(v1, MigrationEvent.MigrationRegistered(migrationId, timestamp)),
            Change(v2, MigrationEvent.ExecutionStarted(executionId, timestamp)),
            Change(v3, MigrationEvent.ExecutionFinished(executionId, timestamp)),
          )
          val processing: Execution.Processing =
            Execution.Processing(executionId, Stats(Set.empty, Set.empty, Set.empty), timestamp)
          val finishedExecution = Execution.Finished(processing, timestamp)
          val state = handler.applyEvents(events)
          val expectedState = Migration(
            migrationId,
            Migration.State(Map(executionId -> finishedExecution)),
            timestamp,
          )
          assert(state)(equalTo(expectedState)) && assert(state.state.status)(equalTo(ExecutionStatus.success))
      }
    },
    test("ExecutionFailed transitions execution to Failed state") {
      check(migrationIdGen, executionIdGen, timestampGen, versionGen, versionGen, versionGen) {
        (migrationId, executionId, timestamp, v1, v2, v3) =>
          val events = NonEmptyList(
            Change(v1, MigrationEvent.MigrationRegistered(migrationId, timestamp)),
            Change(v2, MigrationEvent.ExecutionStarted(executionId, timestamp)),
            Change(v3, MigrationEvent.ExecutionFailed(executionId, timestamp)),
          )
          val processing: Execution.Processing =
            Execution.Processing(executionId, Stats(Set.empty, Set.empty, Set.empty), timestamp)
          val failedExecution = Execution.Failed(processing, timestamp)
          val state = handler.applyEvents(events)
          val expectedState = Migration(
            migrationId,
            Migration.State(Map(executionId -> failedExecution)),
            timestamp,
          )
          assert(state)(equalTo(expectedState)) && assert(state.state.status)(equalTo(ExecutionStatus.execution_failed))
      }
    },
    test("Applying events out of order throws an exception") {
      check(migrationIdGen, executionIdGen, timestampGen, versionGen, versionGen) {
        (migrationId, executionId, timestamp, v1, v2) =>
          val events = NonEmptyList(
            Change(v1, MigrationEvent.ExecutionStarted(executionId, timestamp)),
            Change(v2, MigrationEvent.MigrationRegistered(migrationId, timestamp)),
          )
          assertZIO(ZIO.attempt(handler.applyEvents(events)).exit)(failsWithA[IllegalArgumentException])
      }
    },
    test("Processing invalid event sequence throws an exception") {
      check(migrationIdGen, executionIdGen, keyGen, timestampGen, versionGen, versionGen, versionGen) {
        (migrationId, executionId, key, timestamp, v1, v2, v3) =>
          val events = NonEmptyList(
            Change(v1, MigrationEvent.MigrationRegistered(migrationId, timestamp)),
            Change(v2, MigrationEvent.KeyProcessed(executionId, key)), // Invalid as no ExecutionStarted
            Change(v3, MigrationEvent.ExecutionStarted(executionId, timestamp)),
          )
          assertZIO(ZIO.attempt(handler.applyEvents(events)).exit)(failsWithA[IllegalArgumentException])
      }
    },
  ) @@ TestAspect.samples(1)

}

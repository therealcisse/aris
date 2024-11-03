package com.youtoo
package migration
package model

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*
import zio.*
import com.youtoo.cqrs.*

object MigrationCommandHandlerSpec extends ZIOSpecDefault {

  def spec = suite("MigrationCommandHandlerSpec")(
    test("RegisterMigration command produces MigrationRegistered event") {
      check(migrationIdGen, timestampGen) { (id, timestamp) =>
        val command = MigrationCommand.RegisterMigration(id, timestamp)
        val events = CmdHandler.applyCmd(command)
        val expectedEvent = MigrationEvent.MigrationRegistered(id, timestamp)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("StartExecution command produces ExecutionStarted event") {
      check(executionIdGen, timestampGen) { (id, timestamp) =>
        val command = MigrationCommand.StartExecution(id, timestamp)
        val events = CmdHandler.applyCmd(command)
        val expectedEvent = MigrationEvent.ExecutionStarted(id, timestamp)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("StartProcessingKey command produces ProcessingStarted event") {
      check(executionIdGen, keyGen) { (id, key) =>
        val command = MigrationCommand.StartProcessingKey(id, key)
        val events = CmdHandler.applyCmd(command)
        val expectedEvent = MigrationEvent.ProcessingStarted(id, key)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("ProcessKey command produces KeyProcessed event") {
      check(executionIdGen, keyGen) { (id, key) =>
        val command = MigrationCommand.ProcessKey(id, key)
        val events = CmdHandler.applyCmd(command)
        val expectedEvent = MigrationEvent.KeyProcessed(id, key)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("FailKey command produces ProcessingFailed event") {
      check(executionIdGen, keyGen) { (id, key) =>
        val command = MigrationCommand.FailKey(id, key)
        val events = CmdHandler.applyCmd(command)
        val expectedEvent = MigrationEvent.ProcessingFailed(id, key)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("StopExecution command produces ExecutionStopped event") {
      check(executionIdGen, timestampGen) { (id, timestamp) =>
        val command = MigrationCommand.StopExecution(id, timestamp)
        val events = CmdHandler.applyCmd(command)
        val expectedEvent = MigrationEvent.ExecutionStopped(id, timestamp)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("FinishExecution command produces ExecutionFinished event") {
      check(executionIdGen, timestampGen) { (id, timestamp) =>
        val command = MigrationCommand.FinishExecution(id, timestamp)
        val events = CmdHandler.applyCmd(command)
        val expectedEvent = MigrationEvent.ExecutionFinished(id, timestamp)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("FailExecution command produces ExecutionFailed event") {
      check(executionIdGen, timestampGen) { (id, timestamp) =>
        val command = MigrationCommand.FailExecution(id, timestamp)
        val events = CmdHandler.applyCmd(command)
        val expectedEvent = MigrationEvent.ExecutionFailed(id, timestamp)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("Applying the same command multiple times produces the same event") {
      check(executionIdGen, keyGen) { (id, key) =>
        val command = MigrationCommand.StartProcessingKey(id, key)
        val events1 = CmdHandler.applyCmd(command)
        val events2 = CmdHandler.applyCmd(command)
        assert(events1)(equalTo(events2))
      }
    },
  ) @@ TestAspect.samples(1)
}

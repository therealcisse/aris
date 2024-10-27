package com.youtoo
package migration

import zio.*
import zio.test.*
import zio.prelude.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*

import com.youtoo.migration.model.*

import com.youtoo.cqrs.Codecs.given

// Generators for MigrationCommand components
val migrationIdGen: Gen[Any, Migration.Id] = Gen.fromZIO(Migration.Id.gen.orDie)
val executionIdGen: Gen[Any, Execution.Id] = Gen.fromZIO(Execution.Id.gen.orDie)
val keyGen: Gen[Any, Key] = Gen.fromZIO(Key.gen.orDie)
val timestampGen: Gen[Any, Timestamp] = Gen.fromZIO(Timestamp.now)
val versionGen: Gen[Any, Version] = Gen.fromZIO(Version.gen.orDie)

val validExecutionEventSequence: Gen[Any, List[Change[MigrationEvent]]] =
  for {
    id <- executionIdGen
    version <- versionGen
    timestamp <- timestampGen
    startEvent = Change(version, MigrationEvent.ExecutionStarted(id, timestamp))
    otherEvents0 <- Gen
      .listOfBounded(1, max = 2)(
        keyGen,
      )
      .map {
        _.flatMap { key =>
          List(
            MigrationEvent.ProcessingStarted(id, key),
            MigrationEvent.KeyProcessed(id, key),
          )

        }

      }
    otherEvents1 <- Gen
      .listOfBounded(1, max = 2)(
        keyGen,
      )
      .map {
        _.flatMap { key =>
          List(
            MigrationEvent.ProcessingStarted(id, key),
            MigrationEvent.ProcessingFailed(id, key),
          )

        }

      }
    endEvent <- Gen.oneOf(
      timestampGen.map(ts => MigrationEvent.ExecutionStopped(id, ts)),
      timestampGen.map(ts => MigrationEvent.ExecutionFinished(id, ts)),
      timestampGen.map(ts => MigrationEvent.ExecutionFailed(id, ts)),
    )

    crashed <- Gen.boolean

    changes <- Gen.fromZIO {

      ZIO.foreach(otherEvents0 ++ otherEvents1 ++ (if crashed then Chunk.empty else Chunk(endEvent))) { case event =>
        for {
          v <- Version.gen.orDie
        } yield Change(v, event)
      }
    }
  } yield (startEvent :: changes)

val validMigrationEventSequence: Gen[Any, NonEmptyList[Change[MigrationEvent]]] =
  for {
    id <- migrationIdGen
    version <- versionGen
    timestamp <- timestampGen
    startEvent = Change(version, MigrationEvent.MigrationRegistered(id, timestamp))
    changes0 <- validExecutionEventSequence
    changes1 <- validExecutionEventSequence
    changes2 <- validExecutionEventSequence
  } yield NonEmptyList(startEvent, (changes0 ::: changes1 ::: changes2)*)

def isValidState(state: Migration.State): Boolean =
  // Implement validation logic for the Migration state
  true

val migrationCommandGen: Gen[Any, MigrationCommand] = Gen.oneOf(
  (migrationIdGen <*> timestampGen).map { case (id, ts) => MigrationCommand.RegisterMigration(id, ts) },
  (executionIdGen <*> timestampGen).map { case (id, ts) => MigrationCommand.StartExecution(id, ts) },
  (executionIdGen <*> keyGen).map { case (id, key) => MigrationCommand.StartProcessingKey(id, key) },
  (executionIdGen <*> keyGen).map { case (id, key) => MigrationCommand.ProcessKey(id, key) },
  (executionIdGen <*> keyGen).map { case (id, key) => MigrationCommand.FailKey(id, key) },
  (executionIdGen <*> timestampGen).map { case (id, ts) => MigrationCommand.StopExecution(id, ts) },
  (executionIdGen <*> timestampGen).map { case (id, ts) => MigrationCommand.FinishExecution(id, ts) },
  (executionIdGen <*> timestampGen).map { case (id, ts) => MigrationCommand.FailExecution(id, ts) },
)

val migrationEventGen: Gen[Any, MigrationEvent] = Gen.oneOf(
  (migrationIdGen <*> timestampGen).map { case (id, ts) => MigrationEvent.MigrationRegistered(id, ts) },
  (executionIdGen <*> timestampGen).map { case (id, ts) => MigrationEvent.ExecutionStarted(id, ts) },
  (executionIdGen <*> keyGen).map { case (id, key) => MigrationEvent.ProcessingStarted(id, key) },
  (executionIdGen <*> keyGen).map { case (id, key) => MigrationEvent.KeyProcessed(id, key) },
  (executionIdGen <*> keyGen).map { case (id, key) => MigrationEvent.ProcessingFailed(id, key) },
  (executionIdGen <*> timestampGen).map { case (id, ts) => MigrationEvent.ExecutionStopped(id, ts) },
  (executionIdGen <*> timestampGen).map { case (id, ts) => MigrationEvent.ExecutionFinished(id, ts) },
  (executionIdGen <*> timestampGen).map { case (id, ts) => MigrationEvent.ExecutionFailed(id, ts) },
)

val changeGen: Gen[Any, Change[MigrationEvent]] = (versionGen <*> migrationEventGen).map { case (v, e) => Change(v, e) }

val eventSequenceGen: Gen[Any, NonEmptyList[Change[MigrationEvent]]] =
  Gen.chunkOfBounded(1, 12)(changeGen).map { chunk =>
    NonEmptyList.fromIterableOption(chunk).get
  }

val migrationGen = validMigrationEventSequence map { changes =>
  summon[MigrationEventHandler].applyEvents(changes)
}

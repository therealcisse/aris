package com.youtoo.cqrs
package migration

import cats.implicits.*
import zio.stream.*
import zio.test.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*

import com.youtoo.cqrs.migration.model.*

object DataMigrationSpec extends ZIOSpecDefault {

  def spec = suite("DataMigrationSpec")(
    testExecutionRecordsCreation,
    testSuccessfulMigrationExecution,
    testIncompleteMigrationResumption,
  ).provideLayerShared(DataMigration.live()) @@ TestAspect.withLiveClock @@ TestAspect.ignore

  val testExecutionRecordsCreation = test("Execution Records Creation") {
    inline val executions = 3

    val migrationId = Migration.Id(Key("migration3"))
    val keys = NonEmptyChunk(Key("key1"), (2 to 10).map(i => Key(s"key$i")).toList*)

    val processorExpectations = (
      ProcessorMock.Load(returns = value(ZStream.fromIterable(keys.toList))) ++ ProcessorMock.Count(returns =
        value(10L),
      ) ++ keys.tail.foldLeft(ProcessorMock.Process(equalTo(keys.head), unit)) { case (a, key) =>
        a ++ ProcessorMock.Process(equalTo(key), unit)
      }
    )

    val initialMigration = Migration(
      id = migrationId,
      state = Migration.State(Map.empty),
      timestamp = Timestamp(java.lang.System.currentTimeMillis),
    )

    val migrationCQRSExpectations =
      MigrationCQRSMock
        .Load(equalTo(migrationId), value(initialMigration.some)) ++ MigrationCQRSMock
        .Add(anything, unit)
        .exactly(keys.size)

    val env = (processorExpectations.exactly(executions) ++ migrationCQRSExpectations.exactly(executions)).toLayer

    val testEffect = for {
      _ <- DataMigration.run(migrationId).repeatN(executions)
    } yield assertCompletes

    testEffect.provideSomeLayer[DataMigration](env)
  }

  val testIncompleteMigrationResumption = test("Incomplete Migration Resumption") {
    val migrationId = Migration.Id(Key("migration2"))
    val allKeys = NonEmptyChunk(Key("key1"), (2 to 10).map(i => Key(s"key$i")).toList*)

    val processedKeys = allKeys.take(50)
    val remainingKeys = allKeys.drop(50)

    val initialStats = Stats(
      processing = Set.empty,
      processed = processedKeys.toSet,
      failed = Set.empty,
    )

    val now = java.lang.System.currentTimeMillis

    val previousExecution = Execution.Stopped(
      processing = Execution.Processing(Execution.Id(Key("exec1")), initialStats, Timestamp(now - (3600L * 1000L))),
      timestamp = Timestamp(now - (1800L * 1000L)),
    )
    val initialMigration = Migration(
      id = migrationId,
      state = Migration.State(Map(Execution.Id(Key("exec1")) -> previousExecution)),
      timestamp = Timestamp(now - (3600L * 1000L)),
    )

    val expections =
      ProcessorMock.Count(returns = value(100L)) ++ ProcessorMock.Load(returns = value(ZStream.fromIterable(allKeys)))
    val processorExpectations = NonEmptyChunk
      .fromIterableOption(remainingKeys)
      .fold(
        expections,
      ) { nec =>
        expections ++ nec.tail.foldLeft(ProcessorMock.Process(equalTo(nec.head), unit)) { (a, key) =>
          a ++ ProcessorMock.Process(equalTo(key), unit)

        }
      }

    val migrationCQRSExpectations = MigrationCQRSMock.Load(
      equalTo(migrationId),
      value(initialMigration.some),
    ) ++ MigrationCQRSMock.Add(anything, unit)

    val env = (processorExpectations ++ migrationCQRSExpectations).toLayer

    val testEffect = for {
      _ <- DataMigration.run(migrationId)
    } yield assertCompletes

    testEffect.provideSomeLayer[DataMigration](env)
  }

  val testSuccessfulMigrationExecution = test("Successful Migration Execution") {
    val migrationId = Migration.Id(Key("migration1"))
    val keys = NonEmptyChunk(Key("key1"), (2 to 10).map(i => Key(s"key$i")).toList*)

    val processorExpectations = (
      ProcessorMock.Count(returns = value(100L)) ++ ProcessorMock.Load(returns =
        value(ZStream.fromIterable(keys)),
      ) ++ keys.tail.foldLeft(ProcessorMock.Process(equalTo(keys.head), unit)) { case (a, key) =>
        a ++ ProcessorMock.Process(equalTo(key), unit)
      }
    )

    val initialMigration = Migration(
      id = migrationId,
      state = Migration.State(Map.empty),
      timestamp = Timestamp(java.lang.System.currentTimeMillis),
    )

    val migrationCQRSExpectations = (
      MigrationCQRSMock.Load(equalTo(migrationId), value(initialMigration.some)) ++ MigrationCQRSMock
        .Add(anything, unit)
        .exactly(keys.size)
    )

    val env = (processorExpectations ++ migrationCQRSExpectations).toLayer

    val testEffect = for {
      _ <- DataMigration.run(migrationId)
    } yield assertCompletes

    testEffect.provideSomeLayer[DataMigration](env)
  }
}

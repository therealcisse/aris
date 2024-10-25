package com.youtoo
package migration

import cats.implicits.*
import zio.stream.*
import zio.test.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*

import com.youtoo.migration.model.*

object DataMigrationSpec extends ZIOSpecDefault {

  def spec = suite("DataMigrationSpec")(
    testExecutionRecordsCreation @@ TestAspect.ignore,
    testSuccessfulMigrationExecution @@ TestAspect.ignore,
    testIncompleteMigrationResumption @@ TestAspect.ignore,
    testStopMigration,
  ).provideLayerShared(Interrupter.live() >>> DataMigration.live()) @@ TestAspect.withLiveClock

  val testStopMigration = test("migration is stopped when Interrupter.interrupt is called") {
    for {
      processingStarted <- Promise.make[Nothing, Unit]
      proceed <- Promise.make[Nothing, Unit]
      key = Key("migration1")
      migrationId = Migration.Id(key)
      key1 = Key("key1")
      key2 = Key("key2")

      cmds <- Ref.make(List.empty[MigrationCommand])

      processor = ZLayer.succeed {
        new DataMigration.Processor {
          def count(): Task[Long] = ZIO.succeed(2L)
          def load(): ZStream[Any, Throwable, Key] = ZStream(key1, key2)
          def process(key: Key): Task[Unit] =
            (processingStarted.succeed(()) <* proceed.await).unit
        }
      }

      interrupterRef <- Ref.Synchronized.make(Map.empty[Key, Promise[Throwable, Unit]])
      interrupter = new Interrupter.Live(interrupterRef)
      timestamp <- Timestamp.now
      migration = Migration(migrationId, Migration.State(Map.empty), timestamp)

      migrationCQRS = ZLayer.succeed {

        new MigrationCQRS {
          def add(id: Key, cmd: MigrationCommand): Task[Unit] =
            cmds.update(cmd :: _)
          def load(id: Key): Task[Option[Migration]] = ZIO.succeed(migration.some)

        }
      }

      layer = (processor ++ ZLayer.succeed(new DataMigration.Live(interrupter, 1)) ++ migrationCQRS)
      fiber <- DataMigration.run(migrationId).fork.provideLayer(layer)
      _ <- processingStarted.await
      _ <- DataMigration.stop(migrationId).provideLayer(layer)
      _ <- proceed.succeed(())
      _ <- fiber.join
      c <- cmds.get
    } yield assert(c.headOption)(
      isSome(isSubtype[MigrationCommand.StopExecution](anything)),
    )
  }

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
        .Load(equalTo(migrationId.asKey), value(initialMigration.some)) ++ MigrationCQRSMock
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
      equalTo(migrationId.asKey),
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
      MigrationCQRSMock.Load(equalTo(migrationId.asKey), value(initialMigration.some)) ++ MigrationCQRSMock
        .Add(anything, unit)
        .exactly(keys.size)
    )

    val env = (processorExpectations ++ migrationCQRSExpectations).toLayer

    val testEffect = for {
      _ <- DataMigration.run(migrationId)
    } yield assertCompletes

    testEffect.provideSomeLayer(env)
  }
}

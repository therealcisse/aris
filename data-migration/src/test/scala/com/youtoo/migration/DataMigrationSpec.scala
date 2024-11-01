package com.youtoo
package migration

import cats.implicits.*
import zio.stream.*
import zio.test.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.mock.*
import zio.*

import com.youtoo.std.*

import com.youtoo.migration.model.*
import com.youtoo.migration.service.*

object DataMigrationSpec extends MockSpecDefault {

  def spec = suite("DataMigrationSpec")(
    testExecutionRecordsCreation,
    testSuccessfulMigrationExecution,
    testIncompleteMigrationResumption,
    testStopMigration,
  ).provideLayerShared(
    (Healthcheck.live() ++ Interrupter.live()) >+> DataMigration.live(),
  ) @@ TestAspect.withLiveClock

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

        }
      }

      migrationService = ZLayer.succeed {
        new MigrationService {
          def load(id: Migration.Id): Task[Option[Migration]] = ZIO.succeed(migration.some)
          def loadMany(offset: Option[Key], limit: Long): Task[Chunk[Key]] = ZIO.succeed(Chunk(migration.id.asKey))
          def save(o: Migration): Task[Long] = ZIO.succeed(1L)

        }
      }

      healthcheck <- ZIO.service[Healthcheck]

      layer = (processor ++ ZLayer.succeed(
        new DataMigration.Live(interrupter, healthcheck, 1),
      ) ++ migrationCQRS ++ migrationService)
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

    val migrationServiceMock = MigrationServiceMock.Load(equalTo(migrationId), value(initialMigration.some))

    val migrationCQRSExpectations = MigrationCQRSMock
      .Add(anything, unit)
      .exactly(keys.size)

    val env = (migrationServiceMock ++ processorExpectations.exactly(executions) ++ migrationCQRSExpectations.exactly(
      executions,
    )).toLayer

    val testEffect = for {
      _ <- DataMigration.run(migrationId).repeatN(executions)
    } yield assertCompletes

    testEffect.provideSomeLayer[DataMigration](env)
  } @@ TestAspect.ignore

  val testIncompleteMigrationResumption = test("Incomplete Migration Resumption") {
    val migrationId = Migration.Id(Key("migration2"))
    val allKeys = NonEmptyChunk(Key("key1"), (2 to 10).map(i => Key(s"key$i")).toList*)

    val (processedKeys, remainingKeys) = allKeys.splitAt(50)

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

    val migrationServiceMock = MigrationServiceMock.Load(
      equalTo(migrationId),
      value(initialMigration.some),
    )

    val migrationCQRSExpectations = MigrationCQRSMock.Add(anything, unit)

    val env = (migrationServiceMock ++ processorExpectations ++ migrationCQRSExpectations).toLayer

    val testEffect = for {
      _ <- DataMigration.run(migrationId)
    } yield assertCompletes

    testEffect.provideSomeLayer[DataMigration](env)
  } @@ TestAspect.ignore

  val testSuccessfulMigrationExecution = test("Successful Migration Execution") {
    inline val size = 1L
    val migrationId = Migration.Id(Key("migration1"))
    val keys = NonEmptyChunk(Key("key1"), (2L to size).map(i => Key(s"key$i")).toList*)

    val count = ProcessorMock.Count(returns = value(size))

    val load = ProcessorMock.Load(returns = value(ZStream.fromIterable(keys))).toLayer

    val processorExpectations = keys.tail.foldLeft(
      ProcessorMock.Process(equalTo(keys.head), unit).toLayer ++ MigrationCQRSMock.Add(isArg0(), unit).toLayer,
    ) { case (a, key) =>
      a ++ ProcessorMock.Process(equalTo(key), unit).toLayer ++ MigrationCQRSMock.Add(isArg0(), unit).toLayer
    }

    val initialMigration = Migration(
      id = migrationId,
      state = Migration.State(Map.empty),
      timestamp = Timestamp(java.lang.System.currentTimeMillis),
    )

    val migrationServiceMock = MigrationServiceMock.Load(
      equalTo(migrationId),
      value(initialMigration.some),
    )

    inline def isArg0() = assertion[(Key, MigrationCommand)]("isArg0") { case (_, _) =>
      true
    }

    inline def isArg(key: Key) = assertion[(Key, MigrationCommand)]("isArg") { case (id, _) =>
      id == key
    }

    val startExecution = MigrationCQRSMock.Add(isArg(migrationId.asKey), unit).toLayer
    val finishExecution = MigrationCQRSMock.Add(isArg(migrationId.asKey), unit).toLayer

    val env =
      ((migrationServiceMock ++ count) || (count ++ migrationServiceMock)).toLayer ++ startExecution ++ load ++ processorExpectations ++ finishExecution

    val testEffect = for {
      _ <- DataMigration.run(migrationId)
    } yield assertCompletes

    testEffect.provideSomeLayer(env)
  } @@ TestAspect.ignore

}

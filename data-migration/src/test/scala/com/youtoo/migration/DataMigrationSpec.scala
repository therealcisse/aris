package com.youtoo
package migration

import cats.implicits.*
import zio.test.*
import zio.stream.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.mock.*
import zio.*

import com.youtoo.std.*

import com.youtoo.migration.model.*
import com.youtoo.migration.service.*
import com.youtoo.cqrs.CQRS

object DataMigrationSpec extends MockSpecDefault {

  def spec = suite("DataMigrationSpec")(
    testExecutionRecordsCreation,
    testSuccessfulMigrationExecution,
    testIncompleteMigrationResumption,
    testStopMigration,
  ).provideSomeLayerShared(
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
    check(migrationIdGen, timestampGen, Gen.int(100, 1000)) { case (migrationId, now, n) =>
      val keys = NonEmptyChunk(Key("key1"), (2 to n).map(i => Key(s"key$i")).toList*)

      val initialMigration = Migration(
        id = migrationId,
        state = Migration.State(Map.empty),
        timestamp = now,
      )

      val processor = MockProcessor(keys)
      val cqrs = MockMigrationCQRS()

      val layer =
        MigrationServiceMock.Load(equalTo(migrationId), value(initialMigration.some)).toLayer ++ ZLayer.succeed(
          processor,
        ) ++ ZLayer.succeed(cqrs)

      for {
        service <- ZIO.service[DataMigration]
        _ <- service.run(migrationId).provide(layer)
        calls <- cqrs.getCalls
        seenKeys <- processor.getKeys
      } yield assert(calls.size)(equalTo(n + 2)) && assert(seenKeys)(equalTo(n))

    }

  } @@ TestAspect.ignore

  val testIncompleteMigrationResumption = test("Incomplete Migration Resumption") {
    check(migrationIdGen, timestampGen, Gen.int(100, 1000)) { case (migrationId, now, n) =>
      val allKeys = NonEmptyChunk(Key("key1"), (2 to n).map(i => Key(s"key$i")).toList*)

      val (processedKeys, remainingKeys) = allKeys.splitAt(n / 2)

      val initialStats = Stats(
        processing = Set.empty,
        processed = processedKeys.toSet,
        failed = Set.empty,
      )

      val previousExecution = Execution.Stopped(
        processing =
          Execution.Processing(Execution.Id(Key("exec1")), initialStats, Timestamp(now.value - (3600L * 1000L))),
        timestamp = Timestamp(now.value - (1800L * 1000L)),
      )
      val initialMigration = Migration(
        id = migrationId,
        state = Migration.State(Map(Execution.Id(Key("exec1")) -> previousExecution)),
        timestamp = Timestamp(now.value - (3600L * 1000L)),
      )

      val processor = MockProcessor(remainingKeys)
      val cqrs = MockMigrationCQRS()

      val layer =
        MigrationServiceMock.Load(equalTo(migrationId), value(initialMigration.some)).toLayer ++ ZLayer.succeed(
          processor,
        ) ++ ZLayer.succeed(cqrs)

      for {
        service <- ZIO.service[DataMigration]
        _ <- service.run(migrationId).provide(layer)
        calls <- cqrs.getCalls
        seenKeys <- processor.getKeys
      } yield assert(calls.size)(equalTo(remainingKeys.size + 2)) && assert(seenKeys)(equalTo(Set(remainingKeys*)))

    }

  }

  val testSuccessfulMigrationExecution = test("Successful Migration Execution") {
    check(migrationIdGen, timestampGen, Gen.setOfBounded(1, 1000)(keyGen)) { case (id, timestamp, keys) =>
      val processor = MockProcessor(keys)
      val cqrs = MockMigrationCQRS()

      val migration = Migration(id, Migration.State.empty, timestamp)

      val layer = MigrationServiceMock.Load(equalTo(id), value(migration.some)).toLayer ++ ZLayer.succeed(
        processor,
      ) ++ ZLayer.succeed(cqrs)

      for {
        service <- ZIO.service[DataMigration]
        _ <- service.run(id).provide(layer)
        calls <- cqrs.getCalls
        seenKeys <- processor.getKeys
      } yield assert(calls.size)(equalTo(keys.size + 2)) && assert(seenKeys)(equalTo(keys))

    }
  }

  final class MockProcessor(keysToLoad: Iterable[Key]) extends DataMigration.Processor {
    private val processedKeysRef = Unsafe.unsafe { implicit unsafe =>
      Ref.unsafe.make(Set.empty[Key])
    }

    def count(): Task[Long] = ZIO.succeed(keysToLoad.size.toLong)
    def load(): ZStream[Any, Throwable, Key] = ZStream.fromIterable(keysToLoad)
    def process(key: Key): Task[Unit] = processedKeysRef.update(_ + key)
    def getKeys: Task[Set[Key]] = processedKeysRef.get
  }

  final class MockMigrationCQRS extends MigrationCQRS {
    private val callsRef: Ref[List[(Key, MigrationCommand)]] = Unsafe.unsafe { implicit unsafe =>
      Ref.unsafe.make(List.empty[(Key, MigrationCommand)])
    }

    def add(id: Key, cmd: MigrationCommand): Task[Unit] =
      callsRef.update(calls => (id, cmd) :: calls)

    def getCalls: Task[List[(Key, MigrationCommand)]] =
      callsRef.get
  }
}

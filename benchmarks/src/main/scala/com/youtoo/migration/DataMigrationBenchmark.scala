package com.youtoo
package migration

import cats.implicits.*

import zio.jdbc.*

import org.openjdk.jmh.annotations.{Scope as JmhScope, *}
import zio.*
import java.util.concurrent.TimeUnit

import zio.stream.*

import com.youtoo.std.*

import com.youtoo.migration.model.*
import com.youtoo.cqrs.service.*
import com.youtoo.cqrs.service.postgres.*
import com.youtoo.migration.service.*
import com.youtoo.migration.repository.*
import com.youtoo.cqrs.config.*
import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.*
import com.youtoo.migration.store.*

import zio.profiling.jmh.BenchmarkUtils

@State(JmhScope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.Throughput, Mode.AverageTime))
class DataMigrationBenchmark {
  import DataMigrationBenchmark.*

  var id: Migration.Id = scala.compiletime.uninitialized
  var table: String = scala.compiletime.uninitialized

  @Setup(Level.Trial)
  def setupDatabase(): Unit =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(createDatabase).getOrThrowFiberFailure()
    }

  @Setup(Level.Invocation)
  def setupInvocation(): Unit =

    val result = Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(createMigration).getOrThrowFiberFailure()
    }

    id = result._1
    table = result._2

  @TearDown(Level.Invocation)
  def tearDownInvocation(): Unit =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(dropTable(table)).getOrThrowFiberFailure()
    }

  @Param(
    Array(
      "10",
      "100",
      "1000",
      "10000",
      "1000000",
    ),
  )
  var numRows: Long = scala.compiletime.uninitialized

  @Benchmark
  def benchmarkDataMigrationFetch(): Unit = execute {
    val layer = deps >+> MockProcessor(table, numRows) ++ migrationLayer
    DataMigration.run(id).provideLayer(layer)
  }

}

object DataMigrationBenchmark {
  import zio.logging.*
  import zio.logging.backend.*

  val deps =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j ++ ZLayer.make[MigrationCQRS & FlywayMigration & ZConnectionPool](
      SnapshotStrategy.live(),
      DatabaseConfig.pool,
      MigrationCheckpointer.live(),
      SnapshotStore.live(),
      MigrationEventStore.live(),
      MigrationRepository.live(),
      MigrationService.live(),
      MigrationProvider.live(),
      MigrationCQRS.live(),
      PostgresCQRSPersistence.live(),
      FlywayMigration.live(),
    )

  val createMigration: Task[(Migration.Id, String)] =
    for {
      config <- ZIO.config[DatabaseConfig]

      id <- Key.gen

      tableName <- Key.gen

      timestamp <- Timestamp.now

      cmd =
        MigrationCommand.RegisterMigration(id = Migration.Id(id), timestamp = timestamp)

      op0 = (atomically {

        transaction {
          MigrationCQRS.add(id, cmd)
        }

      })

      op1 = (atomically {

        transaction {
          (s"CREATE TABLE DMB_${tableName.value}" ++ sql"(id TEXT NOT NULL PRIMARY KEY, body TEXT NOT NULL, timestamp BIGINT NOT NULL)").execute

        }

      })

      _ <- (op0 <&> op1).provideLayer(deps)

    } yield (Migration.Id(id), tableName.value)

  def dropTable(name: String): Task[Unit] =
    val op = (atomically {

      transaction {
        (sql"drop table " ++ s"DMB_$name").execute

      }

    })

    op.provideLayer(deps)

  val createDatabase: Task[Unit] =
    for {
      config <- ZIO.config[DatabaseConfig]

      _ <- FlywayMigration.run(config).provideLayer(deps)

    } yield ()

  def MockProcessor(tableName: String, rows: Long): ZLayer[ZConnectionPool, Throwable, DataMigration.Processor] =
    ZLayer.fromFunction { (pool: ZConnectionPool) =>
      new DataMigration.Processor {
        def count(): Task[Long] = ZIO.succeed(rows)

        def load(): ZStream[Any, Throwable, Key] = ZStream.fromIterable((1L to rows).map(a => Key(s"$a")))

        def process(key: Key): Task[Unit] =
          (Timestamp.now <&> zio.Random.nextString(length = 256)) flatMap { case (timestamp, body) =>
            (atomically {

              transaction {
                SqlFragment
                  .insertInto(s"DMB_$tableName")("id", "body", "timestamp")
                  .values((key.value, body, timestamp.value))
                  .insert
                  .unit

              }

            }).provideEnvironment(ZEnvironment(pool))
          }

      }

    }

  class MockInterrupter() extends Interrupter {
    def watch[R, A](id: Key)(f: Promise[Throwable, Unit] => ZIO[R, Throwable, A]): ZIO[R, Throwable, A] =
      ZIO.uninterruptibleMask { restore =>
        Promise.make[Throwable, Unit] flatMap { p =>
          restore(f(p))
        }
      }

    def interrupt(id: Key): Task[Unit] = ZIO.unit

  }

  class MockHealthcheck() extends Healthcheck {
    def start(id: Key, interval: Schedule[Any, Any, Any]): Task[Healthcheck.Handle] =
      ZIO.succeed(Healthcheck.Handle(ZIO.unit))
    def getHeartbeat(id: Key): UIO[Option[Timestamp]] = Timestamp.now.map(_.some)
    def isRunning(id: Key): UIO[Boolean] = ZIO.succeed(true)
  }

  val migrationLayer: ZLayer[Any, Throwable, DataMigration] =
    ZLayer.succeed {
      DataMigration.Live(interrupter = new MockInterrupter(), healthcheck = new MockHealthcheck(), 8)
    }

  def execute(query: ZIO[Any, Throwable, ?]): Unit = BenchmarkUtils.unsafeRun(query)

}

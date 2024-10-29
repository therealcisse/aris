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

  var runtime: Runtime.Scoped[DataMigration] = scala.compiletime.uninitialized

  var id: Migration.Id = scala.compiletime.uninitialized

  @Setup(Level.Trial)
  def setup(): Unit =
    runtime = Unsafe.unsafe { implicit unsafe =>
      Runtime.unsafe.fromLayer(migrationLayer)
    }

    id = Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(init).getOrThrowFiberFailure()
    }

  @TearDown(Level.Trial)
  def tearDown(): Unit =
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.shutdown()
    }

  @Param(Array("10", "100", "1000", "10000", "1000000"))
  var numRows: Long = scala.compiletime.uninitialized

  @Benchmark
  def benchmarkDataMigrationFetch(): Unit = execute {
    val l = runtime.environment.get[DataMigration]

    DataMigration.run(id).provide(ZLayer.succeed(new MockProcessor(numRows)), ZLayer.succeed(l), deps)
  }

}

object DataMigrationBenchmark {
  val deps: ZLayer[Any, Throwable, MigrationCQRS] = ZLayer.make[MigrationCQRS](
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
  )

  val init: Task[Migration.Id] =
    for {
      config <- ZIO.config[DatabaseConfig]

      id <- Migration.Id.gen

      timestamp <- Timestamp.now

      migration =
        Migration(id = id, state = Migration.State.empty, timestamp = timestamp)

      layer = ZLayer.make[MigrationService & FlywayMigration & ZConnectionPool](
        DatabaseConfig.pool,
        MigrationRepository.live(),
        MigrationService.live(),
        FlywayMigration.live(),
      )

      op = (atomically {

        transaction {
          MigrationService.save(migration)
        }

      })

      _ <- (FlywayMigration.run(config) *> op).provideLayer(layer)

    } yield id

  class MockProcessor(rows: Long) extends DataMigration.Processor {
    def count(): Task[Long] = ZIO.succeed(rows)
    def load(): ZStream[Any, Throwable, Key] = ZStream.fromIterable((1L to rows).map(a => Key(s"$a")))
    def process(key: Key): Task[Unit] = ZIO.unit

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

  def execute(query: ZIO[Any, Throwable, ?]): Unit = BenchmarkUtils.unsafeRun(query.fork)

}

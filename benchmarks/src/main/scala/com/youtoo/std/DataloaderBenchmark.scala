package com.youtoo
package std

import org.openjdk.jmh.annotations.{Scope as JmhScope, *}
import zio.*
import java.util.concurrent.TimeUnit

import zio.profiling.jmh.BenchmarkUtils

@State(JmhScope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.Throughput, Mode.AverageTime))
class DataloaderBenchmark {
  import DataloaderBenchmark.*

  var runtime: Runtime.Scoped[Dataloader[Long]] = scala.compiletime.uninitialized

  @Setup(Level.Trial)
  def setup(): Unit =
    runtime = Unsafe.unsafe { implicit unsafe =>
      Runtime.unsafe.fromLayer(Dataloader.live() >>> dataloaderLayer)
    }

  @TearDown(Level.Trial)
  def tearDown(): Unit =
    Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.shutdown()
    }

  @Param(Array("10", "100", "1000", "10000"))
  var numFetchCalls: Long = scala.compiletime.uninitialized

  @Benchmark
  def benchmarkDataloaderFetch(): Unit = execute {
    val l = runtime.environment.get[Dataloader[Long]]

    val fetchAllKeys = ZIO.foreachPar(1L to numFetchCalls)(id => l.fetch(Key(id)))

    fetchAllKeys
  }

}

object DataloaderBenchmark {
  class MockBulkLoader() extends Dataloader.BulkLoader[Long] {
    def loadMany(ids: NonEmptyChunk[Key]): Task[List[Long]] =
      ZIO.succeed(ids.map(_.value).toList)
  }

  given Dataloader.Keyed[Long] with {
    extension (a: Long) def id: Key = Key(a)
  }

  val dataloaderLayer: ZLayer[Dataloader.Factory, Throwable, Dataloader[Long]] =
    ZLayer.scoped {
      for {
        factory <- ZIO.service[Dataloader.Factory]
        dataloader <- factory.createLoader(new MockBulkLoader(), 8)
      } yield dataloader
    }

  private def execute(query: ZIO[Any, Throwable, ?]): Unit =
    BenchmarkUtils.unsafeRun(query)

}

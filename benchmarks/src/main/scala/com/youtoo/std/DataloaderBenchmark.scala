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

  var runtime: Runtime.Scoped[Dataloader[String]] = scala.compiletime.uninitialized

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
  var numFetchCalls: Int = scala.compiletime.uninitialized

  @Benchmark
  def benchmarkDataloaderFetch(): Unit = execute {
    val l = runtime.environment.get[Dataloader[String]]

    val fetchAllKeys = ZIO.foreachPar(1 to numFetchCalls)(id => l.fetch(Key(s"$id")))

    fetchAllKeys
  }

}

object DataloaderBenchmark {
  class MockBulkLoader() extends Dataloader.BulkLoader[String] {
    def loadMany(ids: NonEmptyChunk[Key]): Task[List[String]] =
      ZIO.succeed(ids.map(_.value).toList)
  }

  given Dataloader.Keyed[String] with {
    extension (a: String) def id: Key = Key(a)
  }

  val dataloaderLayer: ZLayer[Dataloader.Factory, Throwable, Dataloader[String]] =
    ZLayer.scoped {
      for {
        factory <- ZIO.service[Dataloader.Factory]
        dataloader <- factory.createLoader(new MockBulkLoader(), 8)
      } yield dataloader
    }

  private def execute(query: ZIO[Any, Throwable, ?]): Unit =
    BenchmarkUtils.unsafeRun(query.fork)

}

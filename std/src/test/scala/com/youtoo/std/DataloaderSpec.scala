package com.youtoo
package std

import zio.*
import zio.test.*
import zio.test.Assertion.*

object DataloaderSpec extends ZIOSpecDefault {
  given Dataloader.Keyed[String] with {
    extension (a: String) def id: Key = Key(a)

  }

  def spec = suite("DataloaderSpec")(
    test("should aggregate multiple fetch calls into a single call to BulkLoader.loadMany") {
      for {
        bulkLoader <- MockBulkLoader[String]
        factory <- ZIO.service[Dataloader.Factory]
        dataloader <- factory.createLoader[String](bulkLoader, 8)
        _ <- ZIO.foreachPar(List(1, 2, 3))(id => dataloader.fetch(Key(s"$id")))
        result <- bulkLoader.getCalls
      } yield assert(result)(hasSize(equalTo(1)) && contains(Set("1", "2", "3")))
    },
    test("should call BulkLoader.loadMany every 15 milliseconds adjusted by loadMany execution time") {
      for {
        bulkLoader <- MockBulkLoader.withFixedDelay(10.millis)
        factory <- ZIO.service[Dataloader.Factory]
        dataloader <- factory.createLoader(bulkLoader, 2)
        fiber <- ZIO.foreachPar((1 to 5))(id => dataloader.fetch(Key(s"$id"))).fork
        _ <- TestClock.adjust(50.millis)
        _ <- fiber.join
        calls <- bulkLoader.getCalls
      } yield assert(calls)(hasSize(isWithin(3, 4))) // Expect 3-4 calls given the timing adjustments
    },
    test("should complete promises with Some if found, None if not found") {
      for {
        bulkLoader <- MockBulkLoader.withData(Map(Key("1") -> "1", Key("3") -> "3"))
        factory <- ZIO.service[Dataloader.Factory]
        dataloader <- factory.createLoader(bulkLoader, 8)

        fiber1 <- dataloader.fetch(Key("1")).fork
        fiber2 <- dataloader.fetch(Key("2")).fork
        fiber3 <- dataloader.fetch(Key("3")).fork

        _ <- TestClock.adjust(50.millis)

        result1 <- fiber1.join
        result2 <- fiber2.join
        result3 <- fiber3.join
      } yield assert(result1)(isSome(equalTo("1"))) &&
        assert(result2)(isNone) &&
        assert(result3)(isSome(equalTo("3")))
    },
    test("should handle multiple calls") {
      for {
        bulkLoader <- MockBulkLoader[String]
        factory <- ZIO.service[Dataloader.Factory]
        dataloader <- factory.createLoader(bulkLoader, 8)
        fetchFiber1 <- dataloader.fetch(Key("1")).fork
        fetchFiber2 <- dataloader.fetch(Key("1")).fork
        _ <- TestClock.adjust(15.millis)
        result <- bulkLoader.getCalls
      } yield assert(result.size)(equalTo(1)) && assert(result)(forall(contains("1")))
    },
    test("should remove cancelled keys from backlog") {
      for {
        bulkLoader <- MockBulkLoader[String]
        factory <- ZIO.service[Dataloader.Factory]
        dataloader <- factory.createLoader(bulkLoader, 8)
        fetchFiber1 <- dataloader.fetch(Key("1")).fork
        fetchFiber2 <- dataloader.fetch(Key("1")).fork
        _ <- fetchFiber1.interrupt // Cancel the fetch
        _ <- fetchFiber2.interrupt // Cancel the fetch
        _ <- TestClock.adjust(15.millis)
        result <- bulkLoader.getCalls
      } yield assert(result.size)(equalTo(0)) && assert(result)(forall(not(contains("1"))))
    } @@ TestAspect.flaky,
    test("should remove only cancelled keys from backlog") {
      for {
        bulkLoader <- MockBulkLoader[String]
        factory <- ZIO.service[Dataloader.Factory]
        dataloader <- factory.createLoader(bulkLoader, 8)
        fetchFiber1 <- dataloader.fetch(Key("1")).fork
        fetchFiber2 <- dataloader.fetch(Key("1")).fork
        _ <- fetchFiber2.interrupt // Cancel the fetch
        _ <- TestClock.adjust(15.millis)
        result <- bulkLoader.getCalls
      } yield assert(result.size)(equalTo(1)) && assert(result)(forall(contains("1")))
    },
    test("should call loadMany as many times as needed") {
      check(Gen.int(1, 36)) { n =>
        for {
          bulkLoader <- MockBulkLoader[String]
          factory <- ZIO.service[Dataloader.Factory]
          dataloader <- factory.createLoader(bulkLoader, 1)
          fiber <- ZIO.foreachPar(1 to n)(i => dataloader.fetch(Key(s"$i"))).fork
          _ <- TestClock.adjust((n * 15).millis)
          calls <- bulkLoader.getCalls
          _ <- fiber.join
        } yield assert(calls.size)(equalTo(n))
      }
    } @@ TestAspect.samples(1),
  ).provideSomeLayerShared[Scope](Dataloader.live())

}

package com.youtoo
package std

import zio.*
import zio.test.*
import zio.test.Assertion.*

object DataloaderSpec extends ZIOSpecDefault {
  given Dataloader.Keyed[Long] with {
    extension (a: Long) def id: Key = Key(a)

  }

  def spec = suite("DataloaderSpec")(
    test("should aggregate multiple fetch calls into a single call to BulkLoader.loadMany") {
      for {
        bulkLoader <- MockBulkLoader[Long]
        factory <- ZIO.service[Dataloader.Factory]
        dataloader <- factory.createLoader[Long](bulkLoader, 8)
        _ <- ZIO.foreachPar(List(1L, 2L, 3L))(id => dataloader.fetch(Key(id)))
        result <- bulkLoader.getCalls
      } yield assert(result)(hasSize(equalTo(1)) && contains(Set(1L, 2L, 3L)))
    },
    test("should call BulkLoader.loadMany every 15 milliseconds adjusted by loadMany execution time") {
      for {
        bulkLoader <- MockBulkLoader.withFixedDelay(10.millis)
        factory <- ZIO.service[Dataloader.Factory]
        dataloader <- factory.createLoader(bulkLoader, 2)
        fiber <- ZIO.foreachPar((1L to 5L))(id => dataloader.fetch(Key(id))).fork
        _ <- TestClock.adjust(50.millis)
        _ <- TestClock.adjust(15.millis)
        _ <- fiber.join
        calls <- bulkLoader.getCalls
      } yield assert(calls)(hasSize(isWithin(3, 4))) // Expect 3-4 calls given the timing adjustments
    } @@ TestAspect.ignore,
    test("should complete promises with Some if found, None if not found") {
      for {
        bulkLoader <- MockBulkLoader.withData(Map(Key(1L) -> 1L, Key(3L) -> 3L))
        factory <- ZIO.service[Dataloader.Factory]
        dataloader <- factory.createLoader(bulkLoader, 8)

        fiber1 <- dataloader.fetch(Key(1L)).fork
        fiber2 <- dataloader.fetch(Key(2L)).fork
        fiber3 <- dataloader.fetch(Key(3L)).fork

        _ <- TestClock.adjust(50.millis)

        result1 <- fiber1.join
        result2 <- fiber2.join
        result3 <- fiber3.join
      } yield assert(result1)(isSome(equalTo(1L))) &&
        assert(result2)(isNone) &&
        assert(result3)(isSome(equalTo(3L)))
    },
    test("should handle multiple calls") {
      for {
        bulkLoader <- MockBulkLoader[Long]
        factory <- ZIO.service[Dataloader.Factory]
        dataloader <- factory.createLoader(bulkLoader, 8)
        fetchFiber1 <- dataloader.fetch(Key(1L)).fork
        fetchFiber2 <- dataloader.fetch(Key(1L)).fork
        _ <- TestClock.adjust(15.millis)
        result <- bulkLoader.getCalls
      } yield assert(result.size)(equalTo(1)) && assert(result)(forall(contains(1L)))
    },
    test("should remove cancelled keys from backlog") {
      for {
        bulkLoader <- MockBulkLoader[Long]
        factory <- ZIO.service[Dataloader.Factory]
        dataloader <- factory.createLoader(bulkLoader, 8)
        fetchFiber1 <- dataloader.fetch(Key(1L)).fork
        fetchFiber2 <- dataloader.fetch(Key(1L)).fork
        _ <- fetchFiber1.interrupt // Cancel the fetch
        _ <- fetchFiber2.interrupt // Cancel the fetch
        _ <- TestClock.adjust(15.millis)
        result <- bulkLoader.getCalls
      } yield assert(result.size)(equalTo(0)) && assert(result)(forall(not(contains(1L))))
    } @@ TestAspect.flaky,
    test("should remove only cancelled keys from backlog") {
      for {
        bulkLoader <- MockBulkLoader[Long]
        factory <- ZIO.service[Dataloader.Factory]
        dataloader <- factory.createLoader(bulkLoader, 8)
        fetchFiber1 <- dataloader.fetch(Key(1L)).fork
        fetchFiber2 <- dataloader.fetch(Key(1L)).fork
        _ <- fetchFiber2.interrupt // Cancel the fetch
        _ <- TestClock.adjust(15.millis)
        result <- bulkLoader.getCalls
      } yield assert(result.size)(equalTo(1)) && assert(result)(forall(contains(1L)))
    },
    test("should call loadMany as many times as needed") {
      check(Gen.int(1, 36)) { n =>
        for {
          bulkLoader <- MockBulkLoader[Long]
          factory <- ZIO.service[Dataloader.Factory]
          dataloader <- factory.createLoader(bulkLoader, 1)
          fiber <- ZIO.foreachPar(1L to n)(i => dataloader.fetch(Key(i))).fork
          _ <- TestClock.adjust((n * 15).millis)
          _ <- TestClock.adjust((15).millis)
          calls <- bulkLoader.getCalls
          _ <- fiber.join
        } yield assert(calls.size)(equalTo(n))
      }
    } @@ TestAspect.ignore,
  ).provideSomeLayerShared[Scope](Dataloader.live())

}

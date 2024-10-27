package com.youtoo
package std

import zio.*

class MockBulkLoader[A: Dataloader.Keyed](initialData: Map[Key, A], delay: Duration) extends Dataloader.BulkLoader[A] {
  private val data = Unsafe.unsafe(implicit unsafe => Ref.unsafe.make(initialData))
  private val calls = Unsafe.unsafe(implicit unsafe => Ref.unsafe.make(Vector.empty[Set[Key]]))

  def loadMany(ids: NonEmptyChunk[Key]): Task[List[A]] = for {
    _ <- ZIO.sleep(delay)
    dataMap <- data.get
    result = ids.toList.flatMap(id => dataMap.get(id))
    _ <- calls.update(c => c :+ Set(ids*))
  } yield result

  def getCalls: UIO[Vector[Set[Key]]] = calls.get

}

object MockBulkLoader {
  def apply[A: Dataloader.Keyed]: UIO[MockBulkLoader[A]] =
    ZIO.succeed(new MockBulkLoader[A](Map.empty, Duration.Zero))

  def withData[A: Dataloader.Keyed](data: Map[Key, A]): UIO[MockBulkLoader[A]] =
    ZIO.succeed(new MockBulkLoader[A](data, Duration.Zero))

  def withDataAndDelay[A: Dataloader.Keyed](data: Map[Key, A], delay: Duration): UIO[MockBulkLoader[A]] =
    ZIO.succeed(new MockBulkLoader[A](data, delay))

  def withFixedDelay[A: Dataloader.Keyed](delay: Duration): UIO[MockBulkLoader[A]] =
    ZIO.succeed(new MockBulkLoader[A](Map.empty, delay))
}

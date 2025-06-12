package com.github
package aris
package projection

import zio.*

trait ProjectionStore {
  def read(id: Projection.Id): Task[Option[Version]]
  def save(id: Projection.Id, version: Version): Task[Int]
}

object ProjectionStore {
  object memory {
    def live(): ZLayer[Any, Nothing, ProjectionStore] =
      ZLayer.fromZIO(Ref.make(Map.empty[Projection.Id, Version]).map(new MemoryProjectionStore(_)))

    class MemoryProjectionStore(ref: Ref[Map[Projection.Id, Version]]) extends ProjectionStore {
      def read(id: Projection.Id): Task[Option[Version]] = ref.get.map(_.get(id))
      def save(id: Projection.Id, version: Version): Task[Int] =
        ref.update(_.updated(id, version)).as(1)
    }
  }
}

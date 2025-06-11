package com.github
package aris
package projection

import zio.*

trait ProjectionManagementStore {
  def pause(id: Projection.Id): Task[Unit]
  def resume(id: Projection.Id): Task[Unit]
  def isPaused(id: Projection.Id): Task[Boolean]
  def offset(id: Projection.Id): Task[Option[Version]]
  def updateOffset(id: Projection.Id, version: Version): Task[Unit]
}

object ProjectionManagementStore {
  final case class State(offset: Version, paused: Boolean)

  object memory {
    def live(): ZLayer[Any, Nothing, ProjectionManagementStore] =
      ZLayer.fromZIO(Ref.make(Map.empty[Projection.Id, State]).map(new MemoryProjectionManagementStore(_)))

    class MemoryProjectionManagementStore(ref: Ref[Map[Projection.Id, State]]) extends ProjectionManagementStore {
      private val defaultState = State(Version.wrap(0L), paused = false)

      def pause(id: Projection.Id): Task[Unit] =
        ref.update(m => m.updated(id, m.getOrElse(id, defaultState).copy(paused = true))).unit

      def resume(id: Projection.Id): Task[Unit] =
        ref.update(m => m.updated(id, m.getOrElse(id, defaultState).copy(paused = false))).unit

      def isPaused(id: Projection.Id): Task[Boolean] =
        ref.get.map(_.get(id).exists(_.paused))

      def offset(id: Projection.Id): Task[Option[Version]] =
        ref.get.map(_.get(id).map(_.offset))

      def updateOffset(id: Projection.Id, version: Version): Task[Unit] =
        ref.update(m =>
          m.updated(id, m.getOrElse(id, defaultState).copy(offset = version))
        ).unit
    }
  }
}

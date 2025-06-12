package com.github
package aris
package projection

import zio.*

final class ProjectionManagement(
  val id: Projection.Id,
  store: ProjectionManagementStore,
  observer: ProjectionManagementObserver = ProjectionManagementObserver.empty,
) {

  def pause(): UIO[Unit] =
    for {
      off <- store.offset(id).map(_.getOrElse(Version.wrap(0L))).orDie
      _   <- observer.paused(id, off)
      _   <- store.pause(id).orDie
    } yield ()

  def resume(): UIO[Unit] =
    for {
      off <- store.offset(id).map(_.getOrElse(Version.wrap(0L))).orDie
      _   <- store.resume(id).orDie
      _   <- observer.resumed(id, off)
    } yield ()

  def isPaused: Task[Boolean] = store.isPaused(id)
  def updateOffset(v: Version): Task[Unit] = store.updateOffset(id, v)
  def offset: Task[Option[Version]] = store.offset(id)
}

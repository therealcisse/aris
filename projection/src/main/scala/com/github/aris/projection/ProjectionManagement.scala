package com.github
package aris
package projection

import zio.*

final class ProjectionManagement(
  val id: Projection.Id,
  store: ProjectionManagementStore,
  observer: ProjectionManagementObserver = ProjectionManagementObserver.empty,
) {

  def stop(): Task[Unit] =
    for {
      off <- store.offset(id).map(_.getOrElse(Version.wrap(0L)))
      _ <- observer.stopped(id, off)
      _ <- store.stop(id)
    } yield ()

  def resume(): Task[Unit] =
    for {
      off <- store.offset(id).map(_.getOrElse(Version.wrap(0L)))
      _ <- store.resume(id)
      _ <- observer.resumed(id, off)
    } yield ()

  def isStopped: Task[Boolean] = store.isStopped(id)
  def updateOffset(v: Version): Task[Unit] = store.updateOffset(id, v)
  def offset: Task[Option[Version]] = store.offset(id)
}

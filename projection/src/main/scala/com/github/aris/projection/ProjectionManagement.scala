package com.github
package aris
package projection

import zio.*

final class ProjectionManagement(
  val id: Projection.Id,
  projection: Projection,
  store: ProjectionManagementStore,
  observer: ProjectionObserver = ProjectionObserver.empty,
) {
  private val fiberRef =
    Unsafe.unsafe { implicit u =>
      Runtime.default.unsafe
        .run(Ref.make[Option[Fiber.Runtime[Throwable, Unit]]](None))
        .getOrThrowFiberFailure()
    }

  def pause(): UIO[Unit] =
    for {
      fiberOpt <- fiberRef.getAndSet(None)
      _        <- ZIO.foreachDiscard(fiberOpt)(_.interrupt)
      off      <- store.offset(id).map(_.getOrElse(Version.wrap(0L))).orDie
      _        <- observer.paused(id, off)
      _        <- store.pause(id).orDie
    } yield ()

  def resume(): ZIO[Scope, Nothing, Unit] =
    ZIO.acquireRelease(
      for {
        _     <- pause()
        off   <- store.offset(id).map(_.getOrElse(Version.wrap(0L))).orDie
        _     <- store.resume(id).orDie
        _     <- observer.resumed(id, off)
        fiber <- projection.run.forkScoped
        _     <- fiberRef.set(Some(fiber))
      } yield ()
    )(_ => pause()).unit

  def isPaused: Task[Boolean] = store.isPaused(id)
  def updateOffset(v: Version): Task[Unit] = store.updateOffset(id, v)
  def offset: Task[Option[Version]] = store.offset(id)
}

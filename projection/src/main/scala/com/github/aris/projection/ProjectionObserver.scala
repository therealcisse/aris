package com.github
package aris
package projection

import zio.*

trait ProjectionObserver {
  def started(id: Projection.Id, offset: Version): UIO[Unit] = ZIO.unit
  def offsetCommitted(id: Projection.Id, offset: Version): UIO[Unit] = ZIO.unit
  def processingError(id: Projection.Id, version: Version, cause: Throwable): UIO[Unit] = ZIO.unit
  def projectionError(id: Projection.Id, cause: Throwable): UIO[Unit] = ZIO.unit
}

object ProjectionObserver {
  val empty: ProjectionObserver = new ProjectionObserver {}
}

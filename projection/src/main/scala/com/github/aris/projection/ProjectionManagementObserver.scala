package com.github
package aris
package projection

import zio.*

trait ProjectionManagementObserver {
  def paused(id: Projection.Id, offset: Version): UIO[Unit] = ZIO.unit
  def resumed(id: Projection.Id, offset: Version): UIO[Unit] = ZIO.unit
}

object ProjectionManagementObserver {
  val empty: ProjectionManagementObserver = new ProjectionManagementObserver {}
}


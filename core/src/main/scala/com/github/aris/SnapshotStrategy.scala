package com.github
package aris

import zio.*

trait SnapshotStrategy {
  def apply(version: Option[Version], n: Int): Boolean

}

object SnapshotStrategy {
  trait Factory {
    def create(discriminator: Discriminator): Task[SnapshotStrategy]

  }

  def live(): ZLayer[Any, Throwable, Factory] =
    ZLayer.succeed {
      new Factory {

        def create(discriminator: Discriminator): Task[SnapshotStrategy] =
          given Config[SnapshotStrategy] =
            Config.int("threshold").nested(discriminator.value, "snapshots") map { threshold =>
              new SnapshotStrategy {
                def apply(version: Option[Version], n: Int): Boolean =
                  n >= threshold

              }

            }

          ZIO.config[SnapshotStrategy]

      }

    }
}

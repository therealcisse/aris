package com.youtoo
package migration
package service

import com.youtoo.migration.model.*

import zio.*

import com.youtoo.cqrs.service.*

trait MigrationCheckpointer extends Checkpointer[Migration] {}

object MigrationCheckpointer {
  inline def save(o: Migration): RIO[MigrationCheckpointer, Unit] = ZIO.serviceWithZIO(_.save(o))

  def live(): ZLayer[MigrationService, Throwable, MigrationCheckpointer] =
    ZLayer.fromFunction { (service: MigrationService) =>
      new MigrationCheckpointer {
        def save(o: Migration): Task[Unit] = service.save(o) as ()

      }
    }

}

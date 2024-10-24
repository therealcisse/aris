package com.youtoo
package migration
package service

import com.youtoo.cqrs.*
import com.youtoo.migration.model.*

import zio.*

import com.youtoo.cqrs.service.*

trait MigrationProvider extends Provider[Migration] {}

object MigrationProvider {
  inline def load(id: Key): RIO[MigrationProvider, Option[Migration]] = ZIO.serviceWithZIO(_.load(id))

  def live(): ZLayer[MigrationService, Throwable, MigrationProvider] =
    ZLayer.fromFunction { (service: MigrationService) =>
      new MigrationProvider {
        def load(id: Key): Task[Option[Migration]] = service.load(Migration.Id(id))

      }
    }

}

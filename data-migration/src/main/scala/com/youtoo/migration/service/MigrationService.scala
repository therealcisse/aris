package com.youtoo
package migration
package service

import com.youtoo.cqrs.service.*

import com.youtoo.migration.model.*
import com.youtoo.migration.repository.*
import com.youtoo.cqrs.*

import zio.*

import zio.jdbc.*

trait MigrationService {
  def load(id: Migration.Id): Task[Option[Migration]]
  def loadMany(offset: Option[Key], limit: Long): Task[Chunk[Key]]
  def save(o: Migration): Task[Long]

}

object MigrationService {
  inline def loadMany(offset: Option[Key], limit: Long): RIO[MigrationService & ZConnection, Chunk[Key]] =
    ZIO.serviceWithZIO[MigrationService](_.loadMany(offset, limit))

  inline def load(id: Migration.Id): RIO[MigrationService & ZConnection, Option[Migration]] =
    ZIO.serviceWithZIO[MigrationService](_.load(id))

  inline def save(o: Migration): RIO[MigrationService & ZConnection, Long] =
    ZIO.serviceWithZIO[MigrationService](_.save(o))

  def live(): ZLayer[ZConnectionPool & MigrationRepository, Throwable, MigrationService] =
    ZLayer.fromFunction { (repository: MigrationRepository, pool: ZConnectionPool) =>
      new MigrationService {
        def load(id: Migration.Id): Task[Option[Migration]] =
          atomically(repository.load(id)).provideEnvironment(ZEnvironment(pool))

        def loadMany(offset: Option[Key], limit: Long): Task[Chunk[Key]] =
          atomically(repository.loadMany(offset, limit)).provideEnvironment(ZEnvironment(pool))

        def save(o: Migration): Task[Long] = atomically(repository.save(o)).provideEnvironment(ZEnvironment(pool))

      }
    }

}

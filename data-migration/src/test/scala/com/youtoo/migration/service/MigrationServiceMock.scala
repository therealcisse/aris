package com.youtoo
package migration
package service

import com.youtoo.migration.model.*

import zio.mock.*

import zio.*

object MigrationServiceMock extends Mock[MigrationService] {

  object Save extends Effect[Migration, Throwable, Long]
  object Load extends Effect[Migration.Id, Throwable, Option[Migration]]
  object LoadMany extends Effect[(Option[Key], Long), Throwable, Chunk[Key]]

  val compose: URLayer[Proxy, MigrationService] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]

      } yield new MigrationService {
        def save(migration: Migration): Task[Long] =
          proxy(Save, migration)

        def load(id: Migration.Id): Task[Option[Migration]] =
          proxy(Load, id)

        def loadMany(offset: Option[Key], limit: Long): Task[Chunk[Key]] =
          proxy(LoadMany, (offset, limit))
      }
    }
}

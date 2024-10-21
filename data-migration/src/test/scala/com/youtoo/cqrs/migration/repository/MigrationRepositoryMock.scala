package com.youtoo.cqrs
package migration
package repository

import zio.*
import com.youtoo.cqrs.migration.model.*

import zio.mock.*

import zio.jdbc.*

object MigrationRepositoryMock extends Mock[MigrationRepository] {

  object Load extends Effect[Migration.Id, Throwable, Option[Migration]]

  object LoadMany extends Effect[(Option[Key], Long), Throwable, Chunk[Key]]

  object Save extends Effect[Migration, Throwable, Long]

  val compose: URLayer[Proxy, MigrationRepository] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new MigrationRepository {
        def load(id: Migration.Id): ZIO[ZConnection, Throwable, Option[Migration]] =
          proxy(Load, id)

        def loadMany(offset: Option[Key], limit: Long): ZIO[ZConnection, Throwable, Chunk[Key]] =
          proxy(LoadMany, (offset, limit))

        def save(o: Migration): ZIO[ZConnection, Throwable, Long] =
          proxy(Save, o)
      }

    }
}

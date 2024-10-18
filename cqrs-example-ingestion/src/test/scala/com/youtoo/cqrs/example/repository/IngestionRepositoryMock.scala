package com.youtoo.cqrs
package example
package repository

import zio.*
import com.youtoo.cqrs.example.model.*

import zio.mock.*

import zio.jdbc.*

object IngestionRepositoryMock extends Mock[IngestionRepository] {

  object Load extends Effect[Ingestion.Id, Throwable, Option[Ingestion]]

  object LoadMany extends Effect[(Option[Key], Long), Throwable, Chunk[Key]]

  object Save extends Effect[Ingestion, Throwable, Long]

  val compose: URLayer[Proxy, IngestionRepository] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new IngestionRepository {
        def load(id: Ingestion.Id): ZIO[ZConnection, Throwable, Option[Ingestion]] =
          proxy(Load, id)

        def loadMany(offset: Option[Key], limit: Long): ZIO[ZConnection, Throwable, Chunk[Key]] =
          proxy(LoadMany, (offset, limit))

        def save(o: Ingestion): ZIO[ZConnection, Throwable, Long] =
          proxy(Save, o)
      }

    }
}

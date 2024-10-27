package com.youtoo
package ingestion
package service

import com.youtoo.ingestion.model.*

import zio.mock.*

import zio.*

object IngestionServiceMock extends Mock[IngestionService] {

  object Save extends Effect[Ingestion, Throwable, Long]
  object Load extends Effect[Ingestion.Id, Throwable, Option[Ingestion]]
  object LoadMany extends Effect[(Option[Key], Long), Throwable, Chunk[Key]]

  val compose: URLayer[Proxy, IngestionService] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]

      } yield new IngestionService {
        def save(ingestion: Ingestion): Task[Long] =
          proxy(Save, ingestion)

        def load(id: Ingestion.Id): Task[Option[Ingestion]] =
          proxy(Load, id)

        def loadMany(offset: Option[Key], limit: Long): Task[Chunk[Key]] =
          proxy(LoadMany, (offset, limit))
      }
    }
}

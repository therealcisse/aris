package com.youtoo.cqrs
package example
package service

import com.youtoo.cqrs.example.model.*
import com.youtoo.cqrs.example.repository.*

import zio.*

import zio.jdbc.*

trait IngestionService {
  def load(id: Ingestion.Id): ZIO[ZConnection, Throwable, Option[Ingestion]]
  def loadMany(offset: Option[Key], limit: Long): ZIO[ZConnection, Throwable, Chunk[Key]]
  def save(o: Ingestion): ZIO[ZConnection, Throwable, Long]

}

object IngestionService {
  inline def loadMany(offset: Option[Key], limit: Long): RIO[IngestionService & ZConnection, Chunk[Key]] =
    ZIO.serviceWithZIO[IngestionService](_.loadMany(offset, limit))

  def live(): ZLayer[IngestionRepository, Throwable, IngestionService] =
    ZLayer.fromFunction { (repository: IngestionRepository) =>
      new IngestionService {
        def load(id: Ingestion.Id): ZIO[ZConnection, Throwable, Option[Ingestion]] = repository.load(id)
        def loadMany(offset: Option[Key], limit: Long): ZIO[ZConnection, Throwable, Chunk[Key]] =
          repository.loadMany(offset, limit)
        def save(o: Ingestion): ZIO[ZConnection, Throwable, Long] = repository.save(o)

      }
    }

}

package com.youtoo
package ingestion
package service

import com.youtoo.cqrs.service.*

import com.youtoo.ingestion.model.*
import com.youtoo.ingestion.repository.*

import zio.*

import zio.jdbc.*

trait IngestionService {
  def load(id: Ingestion.Id): Task[Option[Ingestion]]
  def loadMany(offset: Option[Key], limit: Long): Task[Chunk[Key]]
  def save(o: Ingestion): Task[Long]

}

object IngestionService {
  inline def loadMany(offset: Option[Key], limit: Long): RIO[IngestionService & ZConnection, Chunk[Key]] =
    ZIO.serviceWithZIO[IngestionService](_.loadMany(offset, limit))

  inline def load(id: Ingestion.Id): RIO[IngestionService & ZConnection, Option[Ingestion]] =
    ZIO.serviceWithZIO[IngestionService](_.load(id))

  inline def save(o: Ingestion): RIO[IngestionService & ZConnection, Long] =
    ZIO.serviceWithZIO[IngestionService](_.save(o))

  def live(): ZLayer[ZConnectionPool & IngestionRepository, Throwable, IngestionService] =
    ZLayer.fromFunction { (repository: IngestionRepository, pool: ZConnectionPool) =>
      new IngestionService {
        def load(id: Ingestion.Id): Task[Option[Ingestion]] =
          atomically(repository.load(id)).provideEnvironment(ZEnvironment(pool))

        def loadMany(offset: Option[Key], limit: Long): Task[Chunk[Key]] =
          atomically(repository.loadMany(offset, limit)).provideEnvironment(ZEnvironment(pool))

        def save(o: Ingestion): Task[Long] = atomically(repository.save(o)).provideEnvironment(ZEnvironment(pool))

      }
    }

}

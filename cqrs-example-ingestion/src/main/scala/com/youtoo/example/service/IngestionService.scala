package com.youtoo.cqrs
package example
package service

import com.youtoo.cqrs.example.model.*
import com.youtoo.cqrs.example.repository.*

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.store.*

import zio.*
import zio.prelude.*

import zio.jdbc.*
import com.youtoo.cqrs.service.*

trait IngestionService {
  def load(id: Ingestion.Id): ZIO[ZConnection, Throwable, Option[Ingestion]]
  def save(o: Ingestion): ZIO[ZConnection, Throwable, Long]

}

object IngestionService {

  def live(): ZLayer[IngestionRepository, Throwable, IngestionService] =
    ZLayer.fromFunction { (repository: IngestionRepository) =>
      new IngestionService {
        def load(id: Ingestion.Id): ZIO[ZConnection, Throwable, Option[Ingestion]] = repository.load(id)
        def save(o: Ingestion): ZIO[ZConnection, Throwable, Long] = repository.save(o)

      }
    }

}

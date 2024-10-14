package com.youtoo.cqrs
package example
package service

import com.youtoo.cqrs.example.model.*

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.store.*

import zio.*
import zio.jdbc.*
import zio.prelude.*

import zio.jdbc.*
import com.youtoo.cqrs.service.*

import com.youtoo.cqrs.Codecs.*

trait IngestionProvider extends Provider[Ingestion] {}

object IngestionProvider {

  def live(): ZLayer[CQRSPersistence & IngestionService & ZConnectionPool, Throwable, IngestionProvider] =
    ZLayer.fromFunction { (persistence: CQRSPersistence, service: IngestionService, pool: ZConnectionPool) =>
      new IngestionProvider {
        def load(id: Key): Task[Option[Ingestion]] =
          persistence.atomically {
            service.load(Ingestion.Id(id))

          }.provideEnvironment(ZEnvironment(pool))

      }
    }

}

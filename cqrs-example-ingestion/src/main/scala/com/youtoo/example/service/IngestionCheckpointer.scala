package com.youtoo.cqrs
package example
package service

import com.youtoo.cqrs.example.model.*

import zio.*

import zio.jdbc.*
import com.youtoo.cqrs.service.*
import com.youtoo.cqrs.store.*

trait IngestionCheckpointer extends Checkpointer[Ingestion] {}

object IngestionCheckpointer {

  def live(): ZLayer[CQRSPersistence & IngestionService & ZConnectionPool, Throwable, IngestionCheckpointer] =
    ZLayer.fromFunction { (persistence: CQRSPersistence, service: IngestionService, pool: ZConnectionPool) =>
      new IngestionCheckpointer {
        def save(o: Ingestion): Task[Unit] = persistence.atomically {
          service.save(o) as ()

        }.provideEnvironment(ZEnvironment(pool))

      }
    }

}

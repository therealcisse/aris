package com.youtoo.cqrs
package ingestion
package service

import com.youtoo.ingestion.model.*

import zio.*

import com.youtoo.cqrs.service.*

trait IngestionCheckpointer extends Checkpointer[Ingestion] {}

object IngestionCheckpointer {
  inline def save(o: Ingestion): RIO[IngestionCheckpointer, Unit] = ZIO.serviceWithZIO(_.save(o))

  def live(): ZLayer[IngestionService, Throwable, IngestionCheckpointer] =
    ZLayer.fromFunction { (service: IngestionService) =>
      new IngestionCheckpointer {
        def save(o: Ingestion): Task[Unit] = service.save(o) as ()

      }
    }

}

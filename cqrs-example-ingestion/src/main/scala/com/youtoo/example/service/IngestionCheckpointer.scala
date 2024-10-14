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

  def live()
    : ZLayer[CQRSPersistence & IngestionService & SnapshotStore & ZConnectionPool, Throwable, IngestionCheckpointer] =
    ZLayer.fromFunction {
      (persistence: CQRSPersistence, service: IngestionService, snapshotStore: SnapshotStore, pool: ZConnectionPool) =>
        new IngestionCheckpointer {
          def save(o: Ingestion, version: Version): Task[Unit] = persistence.atomically {
            for {
              _ <- service.save(o)
              _ <- snapshotStore.save(id = o.id.asKey, version)

            } yield ()

          }.provideEnvironment(ZEnvironment(pool))

        }
    }

}

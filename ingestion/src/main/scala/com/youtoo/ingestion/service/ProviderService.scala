package com.youtoo
package ingestion
package service

import cats.implicits.*

import com.youtoo.cqrs.service.*

import com.youtoo.ingestion.model.*
import com.youtoo.ingestion.repository.*

import zio.*

import zio.jdbc.*

import com.youtoo.ingestion.store.*
import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.*

trait ProviderService {
  def load(id: Provider.Id): Task[Option[Provider]]
  def loadMany(offset: Option[Key], limit: Long): Task[Chunk[Provider]]

}

object ProviderService {
  inline def loadMany(offset: Option[Key], limit: Long): RIO[ProviderService & ZConnection, Chunk[Provider]] =
    ZIO.serviceWithZIO[ProviderService](_.loadMany(offset, limit))

  inline def load(id: Provider.Id): RIO[ProviderService, Option[Provider]] =
    ZIO.serviceWithZIO[ProviderService](_.load(id))

  def live(): ZLayer[
    ZConnectionPool & FileEventStore,
    Throwable,
    ProviderService,
  ] =
    ZLayer.fromFunction {
      (
        pool: ZConnectionPool,
        eventStore: FileEventStore,
      ) =>
        val Evt = summon[EventHandler[FileEvent, Option[Provider]]]

        new ProviderService {
          def load(id: Provider.Id): Task[Option[Provider]] =
            val key = id.asKey

            atomically {
              eventStore.readEvents(key)

              ???
            }.provideEnvironment(ZEnvironment(pool))

          def loadMany(offset: Option[Key], limit: Long): Task[Chunk[Provider]] =
            atomically {
              ???
            }.provideEnvironment(ZEnvironment(pool))

        }

    }

}

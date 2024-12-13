package com.youtoo
package ingestion
package service

import cats.implicits.*

import zio.telemetry.opentelemetry.tracing.Tracing

import com.youtoo.cqrs.Codecs.given

import com.youtoo.postgres.*

import com.youtoo.ingestion.model.*

import zio.*
import zio.jdbc.*
import zio.prelude.*

import com.youtoo.ingestion.store.*
import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*

trait ProviderService {
  def addProvider(
    id: Provider.Id,
    name: Provider.Name,
    location: Provider.Location,
  ): Task[Unit]

  def load(id: Provider.Id): Task[Option[Provider]]
  def loadAll(offset: Option[Key], limit: Long): Task[List[Provider]]

  def getFiles(provider: Provider.Id, offset: Option[Key], limit: Long): Task[Option[NonEmptyList[IngestionFile]]]
}

object ProviderService {

  inline def addProvider(
    id: Provider.Id,
    name: Provider.Name,
    location: Provider.Location,
  ): RIO[ProviderService, Unit] =
    ZIO.serviceWithZIO(_.addProvider(id, name, location))

  inline def loadAll(offset: Option[Key], limit: Long): RIO[ProviderService, List[Provider]] =
    ZIO.serviceWithZIO(_.loadAll(offset, limit))

  inline def load(id: Provider.Id): RIO[ProviderService, Option[Provider]] =
    ZIO.serviceWithZIO(_.load(id))

  inline def getFiles(
    provider: Provider.Id,
    offset: Option[Key],
    limit: Long,
  ): RIO[ProviderService, Option[NonEmptyList[IngestionFile]]] =
    ZIO.serviceWithZIO(_.getFiles(provider, offset, limit))

  def live(): ZLayer[
    ZConnectionPool & FileEventStore & Tracing,
    Throwable,
    ProviderService,
  ] =
    ZLayer.fromFunction { (pool: ZConnectionPool, eventStore: FileEventStore, tracing: Tracing) =>
      new ProviderServiceLive(pool, eventStore).traced(tracing)
    }

  class ProviderServiceLive(
    pool: ZConnectionPool,
    eventStore: FileEventStore,
  ) extends ProviderService { self =>

    def addProvider(
      id: Provider.Id,
      name: Provider.Name,
      location: Provider.Location,
    ): Task[Unit] =
      atomically {
        val cmd = FileCommand.AddProvider(id, name, location)
        val evnts = CmdHandler.applyCmd(cmd)

        ZIO.foreachDiscard(evnts) { e =>
          for {
            version <- Version.gen
            ch = Change(version = version, payload = e)
            _ <- eventStore.save(id = id.asKey, ch)
          } yield ()
        }
      }.provideEnvironment(ZEnvironment(pool))

    def load(id: Provider.Id): Task[Option[Provider]] =
      given FileEvent.LoadProvider(id)

      val key = id.asKey

      atomically {
        for {
          events <- eventStore.readEvents(key)
          inn = events flatMap { es =>
            EventHandler.applyEvents(es)
          }
        } yield inn
      }.provideEnvironment(ZEnvironment(pool))

    def loadAll(offset: Option[Key], limit: Long): Task[List[Provider]] =
      given FileEvent.LoadProviders()

      atomically {
        for {
          events <- eventStore.readEvents(
            query = PersistenceQuery.ns(Namespace(1)),
            options = FetchOptions(),
          )
          inn = events.fold(Nil) { es =>
            EventHandler.applyEvents(es)
          }
        } yield inn
      }.provideEnvironment(ZEnvironment(pool))

    def getFiles(
      provider: Provider.Id,
      offset: Option[Key],
      limit: Long,
    ): Task[Option[NonEmptyList[IngestionFile]]] =
      given FileEvent.LoadFiles(provider)

      atomically {
        for {
          events <- eventStore.readEvents(
            query = PersistenceQuery.condition(
              namespace = Namespace(0).some,
              hierarchy = Hierarchy.Child(parentId = provider.asKey).some,
            ),
            options = FetchOptions(),
          )
          inn = events flatMap { es =>
            EventHandler.applyEvents(es)
          }
        } yield inn
      }.provideEnvironment(ZEnvironment(pool))

    def traced(tracing: Tracing): ProviderService =
      new ProviderService {
        def addProvider(
          id: Provider.Id,
          name: Provider.Name,
          location: Provider.Location,
        ): Task[Unit] = self.addProvider(id, name, location) @@ tracing.aspects.span("FileService.addProvider")

        def load(id: Provider.Id): Task[Option[Provider]] = self.load(id) @@ tracing.aspects.span("FileService.load")
        def loadAll(offset: Option[Key], limit: Long): Task[List[Provider]] =
          self.loadAll(offset, limit) @@ tracing.aspects.span("FileService.loadAll")

        def getFiles(
          provider: Provider.Id,
          offset: Option[Key],
          limit: Long,
        ): Task[Option[NonEmptyList[IngestionFile]]] =
          self.getFiles(provider, offset, limit) @@ tracing.aspects.span("FileService.getFiles")
      }

  }

}

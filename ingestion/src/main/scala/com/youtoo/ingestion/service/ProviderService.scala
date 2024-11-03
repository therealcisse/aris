package com.youtoo
package ingestion
package service

import cats.implicits.*

import com.youtoo.cqrs.Codecs.given

import com.youtoo.cqrs.service.*

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
    ZConnectionPool & FileEventStore,
    Throwable,
    ProviderService,
  ] =
    ZLayer.fromFunction {
      (
        pool: ZConnectionPool,
        eventStore: FileEventStore,
      ) =>
        val Cmd = summon[CmdHandler[FileCommand, FileEvent]]

        new ProviderService {
          def addProvider(
            id: Provider.Id,
            name: Provider.Name,
            location: Provider.Location,
          ): Task[Unit] =
            atomically {
              val cmd = FileCommand.AddProvider(id, name, location)
              val evnts = Cmd.applyCmd(cmd)

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
                  ns = NonEmptyList(Namespace(1)).some,
                  hierarchy = None,
                  props = None,
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
                  ns = NonEmptyList(Namespace(0)).some,
                  hierarchy = Hierarchy.Child(parentId = provider.asKey).some,
                  props = None,
                )
                inn = events flatMap { es =>
                  EventHandler.applyEvents(es)
                }

              } yield inn

            }.provideEnvironment(ZEnvironment(pool))

        }

    }

}

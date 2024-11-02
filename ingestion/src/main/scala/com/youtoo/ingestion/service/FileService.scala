package com.youtoo
package ingestion
package service

import cats.implicits.*

import com.youtoo.cqrs.Codecs.given

import com.youtoo.cqrs.service.*

import com.youtoo.ingestion.model.*
import com.youtoo.ingestion.repository.*

import zio.*
import zio.jdbc.*
import zio.prelude.*

import com.youtoo.ingestion.store.*
import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*

trait FileService {
  def addFile(
    provider: Provider.Id,
    id: IngestionFile.Id,
    name: IngestionFile.Name,
    metadata: IngestionFile.Metadata,
    sig: IngestionFile.Sig,
  ): Task[Unit]

  def loadNamed(name: IngestionFile.Name): Task[Option[IngestionFile]]
  def loadSig(sig: IngestionFile.Sig): Task[Option[IngestionFile]]

  def load(id: IngestionFile.Id): Task[Option[IngestionFile]]

  def getFiles(provider: Provider.Id, offset: Option[Key], limit: Long): Task[Chunk[IngestionFile]]

}

object FileService {
  inline def addFile(
    provider: Provider.Id,
    id: IngestionFile.Id,
    name: IngestionFile.Name,
    metadata: IngestionFile.Metadata,
    sig: IngestionFile.Sig,
  ): RIO[FileService, Unit] =
    ZIO.serviceWithZIO(_.addFile(provider, id, name, metadata, sig))

  inline def loadNamed(name: IngestionFile.Name): RIO[FileService, Option[IngestionFile]] =
    ZIO.serviceWithZIO(_.loadNamed(name))

  inline def loadSig(sig: IngestionFile.Sig): RIO[FileService, Option[IngestionFile]] =
    ZIO.serviceWithZIO(_.loadSig(sig))

  inline def getFiles(
    provider: Provider.Id,
    offset: Option[Key],
    limit: Long,
  ): RIO[FileService & ZConnection, Chunk[IngestionFile]] =
    ZIO.serviceWithZIO[FileService](_.getFiles(provider, offset, limit))

  inline def load(id: IngestionFile.Id): RIO[FileService, Option[IngestionFile]] =
    ZIO.serviceWithZIO[FileService](_.load(id))

  def live(): ZLayer[
    ZConnectionPool & FileEventStore,
    Throwable,
    FileService,
  ] =
    ZLayer.fromFunction {
      (
        pool: ZConnectionPool,
        eventStore: FileEventStore,
      ) =>
        val Cmd = summon[CmdHandler[FileCommand, FileEvent]]
        val Evt = summon[EventHandler[FileEvent, Option[IngestionFile]]]

        new FileService {
          def addFile(
            provider: Provider.Id,
            id: IngestionFile.Id,
            name: IngestionFile.Name,
            metadata: IngestionFile.Metadata,
            sig: IngestionFile.Sig,
          ): Task[Unit] =
            atomically {
              val cmd = FileCommand.AddFile(provider, id, name, metadata, sig)
              val evnts = Cmd.applyCmd(cmd)

              ZIO.foreachDiscard(evnts) { e =>
                for {
                  version <- Version.gen
                  ch = Change(version = version, payload = e)
                  _ <- eventStore.save(id = id.asKey, ch)
                } yield ()

              }

            }.provideEnvironment(ZEnvironment(pool))

          def loadNamed(name: IngestionFile.Name): Task[Option[IngestionFile]] =
            atomically {
              for {
                events <- eventStore.readEvents(
                  ns = NonEmptyList(Namespace(0)).some,
                  hierarchy = None,
                  props = NonEmptyList(EventProperty("name", name.value)).some,
                )
                inn = events flatMap { es =>
                  Evt.applyEvents(es)
                }

              } yield inn

            }.provideEnvironment(ZEnvironment(pool))

          def loadSig(sig: IngestionFile.Sig): Task[Option[IngestionFile]] =
            atomically {
              for {
                events <- eventStore.readEvents(
                  ns = NonEmptyList(Namespace(0)).some,
                  hierarchy = None,
                  props = NonEmptyList(EventProperty("sig", sig.value)).some,
                )
                inn = events flatMap { es =>
                  Evt.applyEvents(es)
                }

              } yield inn

            }.provideEnvironment(ZEnvironment(pool))

          def load(id: IngestionFile.Id): Task[Option[IngestionFile]] =
            val key = id.asKey

            atomically {
              for {
                events <- eventStore.readEvents(key)
                inn = events flatMap { es =>
                  Evt.applyEvents(es)
                }

              } yield inn

            }.provideEnvironment(ZEnvironment(pool))

          def getFiles(provider: Provider.Id, offset: Option[Key], limit: Long): Task[Chunk[IngestionFile]] =
            atomically {
              ???
            }.provideEnvironment(ZEnvironment(pool))

        }

    }

}

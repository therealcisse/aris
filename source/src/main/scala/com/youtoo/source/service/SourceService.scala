package com.youtoo
package source
package service

import zio.*
import zio.jdbc.*

import com.youtoo.postgres.*
import com.youtoo.cqrs.*
import com.youtoo.source.model.*
import com.youtoo.source.store.*

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*

trait SourceService {
  def addSource(id: SourceDefinition.Id, info: SourceType): Task[Unit]
  def deleteSource(id: SourceDefinition.Id): Task[Unit]
  def load(id: SourceDefinition.Id): Task[Option[SourceDefinition]]
  def loadAll(): Task[List[SourceDefinition]]
}

object SourceService {

  def addSource(id: SourceDefinition.Id, info: SourceType): RIO[SourceService, Unit] =
    ZIO.serviceWithZIO[SourceService](_.addSource(id, info))

  def deleteSource(id: SourceDefinition.Id): RIO[SourceService, Unit] =
    ZIO.serviceWithZIO[SourceService](_.deleteSource(id))

  def load(id: SourceDefinition.Id): RIO[SourceService, Option[SourceDefinition]] =
    ZIO.serviceWithZIO[SourceService](_.load(id))

  def loadAll(): RIO[SourceService, List[SourceDefinition]] =
    ZIO.serviceWithZIO[SourceService](_.loadAll())

  def live(): ZLayer[SourceCQRS & SourceEventStore & ZConnectionPool & Tracing, Nothing, SourceService] =
    ZLayer.fromFunction {
      (
        cqrs: SourceCQRS,
        eventStore: SourceEventStore,
        pool: ZConnectionPool,
        tracing: Tracing,
      ) =>
        SourceServiceLive(cqrs, eventStore, pool).traced(tracing)
    }

  final class SourceServiceLive(
    cqrs: SourceCQRS,
    eventStore: SourceEventStore,
    pool: ZConnectionPool,
  ) extends SourceService { self =>
    def addSource(id: SourceDefinition.Id, info: SourceType): Task[Unit] =
      val cmd = SourceCommand.AddOrModify(id, info)
      cqrs.add(id.asKey, cmd)

    def deleteSource(id: SourceDefinition.Id): Task[Unit] =
      val cmd = SourceCommand.Delete(id)
      cqrs.add(id.asKey, cmd)

    def load(id: SourceDefinition.Id): Task[Option[SourceDefinition]] =
      for {
        events <- eventStore
          .readEvents(
            id.asKey,
            query = PersistenceQuery.anyNamespace(SourceEvent.NS.Added, SourceEvent.NS.Deleted),
            options = FetchOptions(),
          )
          .atomically
          .provideEnvironment(ZEnvironment(pool))

        source = events.fold(None) { es =>
          EventHandler.applyEvents(es)(using SourceEvent.LoadSource())
        }

      } yield source

    def loadAll(): Task[List[SourceDefinition]] =
      for {
        events <- eventStore
          .readEvents(
            query = PersistenceQuery.anyNamespace(SourceEvent.NS.Added, SourceEvent.NS.Deleted),
            options = FetchOptions(),
          )
          .atomically
          .provideEnvironment(ZEnvironment(pool))

        sources = events.fold(Nil) { es =>
          EventHandler.applyEvents(es)(using SourceEvent.LoadSources()).values.toList
        }

      } yield sources

    def traced(tracing: Tracing): SourceService = new SourceService {
      def addSource(id: SourceDefinition.Id, info: SourceType): Task[Unit] =
        self.addSource(id, info) @@ tracing.aspects.span(
          "SourceService.addSource",
          attributes = Attributes(Attribute.long("sourceId", id.asKey.value)),
        )
      def deleteSource(id: SourceDefinition.Id): Task[Unit] =
        self.deleteSource(id) @@ tracing.aspects.span(
          "SourceService.deleteSource",
          attributes = Attributes(Attribute.long("sourceId", id.asKey.value)),
        )
      def load(id: SourceDefinition.Id): Task[Option[SourceDefinition]] =
        self.load(id) @@ tracing.aspects.span(
          "SourceService.load",
          attributes = Attributes(Attribute.long("sourceId", id.asKey.value)),
        )
      def loadAll(): Task[List[SourceDefinition]] =
        self.loadAll() @@ tracing.aspects.span("SourceService.loadAll")
    }
  }

}

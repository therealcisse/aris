package com.youtoo
package sink
package service

import zio.*
import zio.jdbc.*

import com.youtoo.postgres.*
import com.youtoo.cqrs.*
import com.youtoo.sink.model.*
import com.youtoo.sink.store.*

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*

trait SinkService {
  def addSink(id: SinkDefinition.Id, info: SinkType): Task[Unit]
  def deleteSink(id: SinkDefinition.Id): Task[Unit]
  def load(id: SinkDefinition.Id): Task[Option[SinkDefinition]]
  def loadAll(): Task[List[SinkDefinition]]
}

object SinkService {

  def addSink(id: SinkDefinition.Id, info: SinkType): RIO[SinkService, Unit] =
    ZIO.serviceWithZIO[SinkService](_.addSink(id, info))

  def deleteSink(id: SinkDefinition.Id): RIO[SinkService, Unit] =
    ZIO.serviceWithZIO[SinkService](_.deleteSink(id))

  def load(id: SinkDefinition.Id): RIO[SinkService, Option[SinkDefinition]] =
    ZIO.serviceWithZIO[SinkService](_.load(id))

  def loadAll(): RIO[SinkService, List[SinkDefinition]] =
    ZIO.serviceWithZIO[SinkService](_.loadAll())

  def live(): ZLayer[SinkCQRS & SinkEventStore & ZConnectionPool & Tracing, Nothing, SinkService] =
    ZLayer.fromFunction {
      (
        cqrs: SinkCQRS,
        eventStore: SinkEventStore,
        pool: ZConnectionPool,
        tracing: Tracing,
      ) =>
        SinkServiceLive(cqrs, eventStore, pool).traced(tracing)
    }

  final class SinkServiceLive(
    cqrs: SinkCQRS,
    eventStore: SinkEventStore,
    pool: ZConnectionPool,
  ) extends SinkService { self =>
    def addSink(id: SinkDefinition.Id, info: SinkType): Task[Unit] =
      val cmd = SinkCommand.AddOrModify(id, info)
      cqrs.add(id.asKey, cmd)

    def deleteSink(id: SinkDefinition.Id): Task[Unit] =
      val cmd = SinkCommand.Delete(id)
      cqrs.add(id.asKey, cmd)

    def load(id: SinkDefinition.Id): Task[Option[SinkDefinition]] =
      for {
        events <- eventStore
          .readEvents(
            id.asKey,
            query = PersistenceQuery.anyNamespace(SinkEvent.NS.Added, SinkEvent.NS.Deleted),
            options = FetchOptions(),
          )
          .atomically
          .provideEnvironment(ZEnvironment(pool))

        sink = events.fold(None) { es =>
          EventHandler.applyEvents(es)(using SinkEvent.LoadSink())
        }

      } yield sink

    def loadAll(): Task[List[SinkDefinition]] =
      for {
        events <- eventStore
          .readEvents(
            query = PersistenceQuery.anyNamespace(SinkEvent.NS.Added, SinkEvent.NS.Deleted),
            options = FetchOptions(),
          )
          .atomically
          .provideEnvironment(ZEnvironment(pool))

        sinks = events.fold(Nil) { es =>
          EventHandler.applyEvents(es)(using SinkEvent.LoadSinks()).values.toList
        }

      } yield sinks

    def traced(tracing: Tracing): SinkService = new SinkService {
      def addSink(id: SinkDefinition.Id, info: SinkType): Task[Unit] =
        self.addSink(id, info) @@ tracing.aspects.span(
          "SinkService.addSink",
          attributes = Attributes(Attribute.long("sinkId", id.asKey.value)),
        )
      def deleteSink(id: SinkDefinition.Id): Task[Unit] =
        self.deleteSink(id) @@ tracing.aspects.span(
          "SinkService.deleteSink",
          attributes = Attributes(Attribute.long("sinkId", id.asKey.value)),
        )
      def load(id: SinkDefinition.Id): Task[Option[SinkDefinition]] =
        self.load(id) @@ tracing.aspects.span(
          "SinkService.load",
          attributes = Attributes(Attribute.long("sinkId", id.asKey.value)),
        )
      def loadAll(): Task[List[SinkDefinition]] =
        self.loadAll() @@ tracing.aspects.span("SinkService.loadAll")
    }
  }

}

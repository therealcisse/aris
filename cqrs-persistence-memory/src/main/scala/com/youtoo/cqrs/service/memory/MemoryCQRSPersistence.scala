package com.youtoo
package cqrs
package service
package memory

import zio.telemetry.opentelemetry.tracing.Tracing

import com.youtoo.cqrs.domain.*

import zio.schema.codec.*

import zio.prelude.*

trait MemoryCQRSPersistence extends CQRSPersistence {}

object MemoryCQRSPersistence {
  import zio.*
  import zio.jdbc.*
  import zio.schema.*

  def live(): ZLayer[Tracing, Throwable, CQRSPersistence] =
    ZLayer.fromFunction(new MemoryCQRSPersistenceLive().traced(_))

  class MemoryCQRSPersistenceLive() extends CQRSPersistence { self =>
    def readEvents[Event: BinaryCodec: Tag](
      id: Key,
      discriminator: Discriminator,
    ): RIO[ZConnection, Chunk[Change[Event]]] =
      ???

    def readEvents[Event: BinaryCodec: Tag](
      id: Key,
      discriminator: Discriminator,
      snapshotVersion: Version,
    ): RIO[ZConnection, Chunk[Change[Event]]] =
      ???

    def readEvents[Event: BinaryCodec: Tag](
      discriminator: Discriminator,
      ns: Option[NonEmptyList[Namespace]],
      hierarchy: Option[Hierarchy],
      props: Option[NonEmptyList[EventProperty]],
    ): RIO[ZConnection, Chunk[Change[Event]]] =
      ???

    def readEvents[Event: BinaryCodec: Tag](
      discriminator: Discriminator,
      snapshotVersion: Version,
      ns: Option[NonEmptyList[Namespace]],
      hierarchy: Option[Hierarchy],
      props: Option[NonEmptyList[EventProperty]],
    ): RIO[ZConnection, Chunk[Change[Event]]] =
      ???

    def saveEvent[Event: BinaryCodec: MetaInfo: Tag](
      id: Key,
      discriminator: Discriminator,
      event: Change[Event],
    ): RIO[ZConnection, Long] =
      ???

    def readSnapshot(id: Key): RIO[ZConnection, Option[Version]] =
      ???

    def saveSnapshot(id: Key, version: Version): RIO[ZConnection, Long] =
      ???

    def traced(tracing: Tracing): CQRSPersistence =
      new CQRSPersistence {
        def readEvents[Event: BinaryCodec: Tag](
          id: Key,
          discriminator: Discriminator,
        ): RIO[ZConnection, Chunk[Change[Event]]] =
          self.readEvents(id, discriminator) @@ tracing.aspects.span("MemoryCQRSPersistence.readEvents")

        def readEvents[Event: BinaryCodec: Tag](
          id: Key,
          discriminator: Discriminator,
          snapshotVersion: Version,
        ): RIO[ZConnection, Chunk[Change[Event]]] =
          self.readEvents(id, discriminator, snapshotVersion) @@ tracing.aspects.span(
            "MemoryCQRSPersistence.readEvents.fromSnapshot",
          )

        def readEvents[Event: BinaryCodec: Tag](
          discriminator: Discriminator,
          ns: Option[NonEmptyList[Namespace]],
          hierarchy: Option[Hierarchy],
          props: Option[NonEmptyList[EventProperty]],
        ): RIO[ZConnection, Chunk[Change[Event]]] =
          self.readEvents(discriminator, ns, hierarchy, props) @@ tracing.aspects.span(
            "MemoryCQRSPersistence.readEvents.query",
          )

        def readEvents[Event: BinaryCodec: Tag](
          discriminator: Discriminator,
          snapshotVersion: Version,
          ns: Option[NonEmptyList[Namespace]],
          hierarchy: Option[Hierarchy],
          props: Option[NonEmptyList[EventProperty]],
        ): RIO[ZConnection, Chunk[Change[Event]]] =
          self.readEvents(discriminator, snapshotVersion, ns, hierarchy, props) @@ tracing.aspects.span(
            "MemoryCQRSPersistence.readEvents.query_fromSnapshot",
          )

        def saveEvent[Event: BinaryCodec: MetaInfo: Tag](
          id: Key,
          discriminator: Discriminator,
          event: Change[Event],
        ): RIO[ZConnection, Long] =
          self.saveEvent(id, discriminator, event) @@ tracing.aspects.span("MemoryCQRSPersistence.saveEvent")

        def readSnapshot(id: Key): RIO[ZConnection, Option[Version]] =
          self.readSnapshot(id) @@ tracing.aspects.span("MemoryCQRSPersistence.readSnapshot")

        def saveSnapshot(id: Key, version: Version): RIO[ZConnection, Long] =
          self.saveSnapshot(id, version) @@ tracing.aspects.span("MemoryCQRSPersistence.saveSnapshot")
      }

  }

}

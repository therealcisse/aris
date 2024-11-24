package com.youtoo
package cqrs
package service
package memory

import cats.implicits.*

import zio.telemetry.opentelemetry.tracing.Tracing

import com.youtoo.cqrs.domain.*

import zio.schema.codec.*

import zio.prelude.*
import scala.collection.immutable.MultiDict

trait MemoryCQRSPersistence extends CQRSPersistence {}

object MemoryCQRSPersistence {
  import zio.*
  import zio.jdbc.*
  import zio.schema.*

  type State = State.Type

  object State extends Newtype[State.Info] {
    case class Info(events: Map[Discriminator, MultiDict[Key, Change[?]]], snapshots: Map[Key, Version])

    def empty: State = State(Info(Map.empty, Map.empty))

    extension (s: State)
      def getSnapshot(id: Key): Option[Version] =
        val p = State.unwrap(s)
        p.snapshots.get(id)

    extension (s: State)
      def setSnapshot(id: Key, version: Version): State =
        val p = State.unwrap(s)

        State(p.copy(snapshots = p.snapshots + (id -> version)))

    extension (s: State)
      def add[Event](id: Key, discriminator: Discriminator, event: Change[Event]): State =
        val p = State.unwrap(s)

        val r = p.events.updatedWith(discriminator) {
          case None => MultiDict(id -> event).some
          case Some(map) => (map + (id -> event)).some

        }

        State(p.copy(events = r))

    extension (s: State)
      def fetch[Event](id: Key, discriminator: Discriminator): Chunk[Change[Event]] =
        val p = State.unwrap(s)

        p.events.get(discriminator) match {
          case None => Chunk()
          case Some(map) => Chunk.fromIterable(map.get(id).asInstanceOf[Set[Change[Event]]]).sorted
        }

    extension (s: State)
      def fetch[Event](
        id: Key,
        discriminator: Discriminator,
        snapshotVersion: Version,
      ): Chunk[Change[Event]] =
        val p = s.fetch[Event](id, discriminator)
        p.filter(_.version.value > snapshotVersion.value)

    extension (s: State)
      def fetch[Event: MetaInfo](
        discriminator: Discriminator,
        ns: Option[NonEmptyList[Namespace]],
        hierarchy: Option[Hierarchy],
        props: Option[NonEmptyList[EventProperty]],
      ): Chunk[Change[Event]] =
        val p = State.unwrap(s)

        p.events.get(discriminator) match {
          case None => Chunk()
          case Some(map) =>
            val all = map.sets.values.flatten.asInstanceOf[Iterable[Change[Event]]]

            val matches = all.filter { ch =>

              val nsMatch = ns match {
                case None => true
                case Some(value) => value.toSet.contains(ch.payload.namespace)
              }

              val hierarchyMatch = hierarchy match {
                case None => true
                case h =>
                  (ch.payload.hierarchy, h).tupled match {
                    case None => false

                    case Some(Hierarchy.Descendant(gpId0, pId0), Hierarchy.Descendant(gpId1, pId1)) =>
                      gpId0 == gpId1 && pId0 == pId1
                    case Some(Hierarchy.Descendant(_, pId0), Hierarchy.Child(pId1)) => pId0 == pId1
                    case Some(Hierarchy.Descendant(gpId0, _), Hierarchy.GrandChild(gpId1)) => gpId0 == gpId1

                    case Some(Hierarchy.Child(pId0), Hierarchy.Child(pId1)) => pId0 == pId1
                    case Some(Hierarchy.GrandChild(gpId0), Hierarchy.GrandChild(gpId1)) => gpId0 == gpId1

                    case _ => false
                  }
              }

              val propsMatch = props match {
                case None => true
                case Some(p) => p.toChunk == ch.payload.props
              }

              nsMatch && hierarchyMatch && propsMatch
            }

            Chunk.fromIterable(matches).sorted
        }

    extension (s: State)
      def fetch[Event: MetaInfo](
        discriminator: Discriminator,
        snapshotVersion: Version,
        ns: Option[NonEmptyList[Namespace]],
        hierarchy: Option[Hierarchy],
        props: Option[NonEmptyList[EventProperty]],
      ): Chunk[Change[Event]] =
        val p = s.fetch[Event](discriminator, ns, hierarchy, props)
        p.filter(_.version.value > snapshotVersion.value)

  }

  def live(): ZLayer[Tracing, Throwable, CQRSPersistence] =
    ZLayer {
      for {
        tracing <- ZIO.service[Tracing]
        ref <- Ref.Synchronized.make(State.empty)

      } yield new MemoryCQRSPersistenceLive(ref).traced(tracing)

    }

  class MemoryCQRSPersistenceLive(ref: Ref.Synchronized[State]) extends CQRSPersistence { self =>
    def readEvents[Event: BinaryCodec: Tag: MetaInfo](
      id: Key,
      discriminator: Discriminator,
    ): RIO[ZConnection, Chunk[Change[Event]]] =
      ref.get.map(_.fetch(id, discriminator))

    def readEvents[Event: BinaryCodec: Tag: MetaInfo](
      id: Key,
      discriminator: Discriminator,
      snapshotVersion: Version,
    ): RIO[ZConnection, Chunk[Change[Event]]] =
      ref.get.map(_.fetch(id, discriminator, snapshotVersion))

    def readEvents[Event: BinaryCodec: Tag: MetaInfo](
      discriminator: Discriminator,
      ns: Option[NonEmptyList[Namespace]],
      hierarchy: Option[Hierarchy],
      props: Option[NonEmptyList[EventProperty]],
    ): RIO[ZConnection, Chunk[Change[Event]]] =
      ref.get.map(_.fetch(discriminator, ns, hierarchy, props))

    def readEvents[Event: BinaryCodec: Tag: MetaInfo](
      discriminator: Discriminator,
      snapshotVersion: Version,
      ns: Option[NonEmptyList[Namespace]],
      hierarchy: Option[Hierarchy],
      props: Option[NonEmptyList[EventProperty]],
    ): RIO[ZConnection, Chunk[Change[Event]]] =
      ref.get.map(_.fetch(discriminator, snapshotVersion, ns, hierarchy, props))

    def saveEvent[Event: BinaryCodec: MetaInfo: Tag](
      id: Key,
      discriminator: Discriminator,
      event: Change[Event],
    ): RIO[ZConnection, Long] =
      ref.update(_.add(id, discriminator, event)) `as` 1L

    def readSnapshot(id: Key): RIO[ZConnection, Option[Version]] =
      ref.get.map(_.getSnapshot(id))

    def saveSnapshot(id: Key, version: Version): RIO[ZConnection, Long] =
      ref.update(_.setSnapshot(id, version)) `as` 1L

    def traced(tracing: Tracing): CQRSPersistence =
      new CQRSPersistence {
        def readEvents[Event: BinaryCodec: Tag: MetaInfo](
          id: Key,
          discriminator: Discriminator,
        ): RIO[ZConnection, Chunk[Change[Event]]] =
          self.readEvents(id, discriminator) @@ tracing.aspects.span("MemoryCQRSPersistence.readEvents")

        def readEvents[Event: BinaryCodec: Tag: MetaInfo](
          id: Key,
          discriminator: Discriminator,
          snapshotVersion: Version,
        ): RIO[ZConnection, Chunk[Change[Event]]] =
          self.readEvents(id, discriminator, snapshotVersion) @@ tracing.aspects.span(
            "MemoryCQRSPersistence.readEvents.fromSnapshot",
          )

        def readEvents[Event: BinaryCodec: Tag: MetaInfo](
          discriminator: Discriminator,
          ns: Option[NonEmptyList[Namespace]],
          hierarchy: Option[Hierarchy],
          props: Option[NonEmptyList[EventProperty]],
        ): RIO[ZConnection, Chunk[Change[Event]]] =
          self.readEvents(discriminator, ns, hierarchy, props) @@ tracing.aspects.span(
            "MemoryCQRSPersistence.readEvents.query",
          )

        def readEvents[Event: BinaryCodec: Tag: MetaInfo](
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

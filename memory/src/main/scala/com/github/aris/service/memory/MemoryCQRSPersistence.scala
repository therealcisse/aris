package com.github
package aris
package service
package memory

import cats.implicits.*

import com.github.aris.domain.*

import zio.schema.codec.*

import zio.prelude.{Ordering as _, *}
import scala.collection.immutable.MultiDict

trait MemoryCQRSPersistence extends CQRSPersistence {}

object MemoryCQRSPersistence {
  import zio.*
  import zio.schema.*

  extension [Event: MetaInfo](q: PersistenceQuery.Condition) def isMatch(ch: Change[Event]): Boolean =
    val refMatch = q.reference match {
      case None => true
      case Some(value) => ch.payload.reference.contains(value)
    }

    val nsMatch = q.namespace match {
      case None => true
      case Some(value) => ch.payload.namespace == value
    }

    val hierarchyMatch = q.hierarchy match {
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

    val propsMatch = q.props match {
      case None => true
      case Some(p) => p.toChunk == ch.payload.props
    }

    refMatch && nsMatch && hierarchyMatch && propsMatch

  extension [Event: MetaInfo](q: PersistenceQuery) def isMatch(ch: Change[Event]): Boolean = q match {
    case condition: PersistenceQuery.Condition => condition.isMatch(ch)
    case PersistenceQuery.any(condition, more*) =>
      more.foldLeft(condition.isMatch(ch)) {
        case (a, n) => a || n.isMatch(ch)

      }

    case PersistenceQuery.forall(query, more*) =>
      more.foldLeft(query.isMatch(ch)) {
        case (a, n) => a && n.isMatch(ch)

      }


  }

  type State = State.Type

  object State extends Newtype[State.Info] {
    case class EntryKey(catalog: Catalog, discriminator: Discriminator)

    case class Info(
      events: Map[EntryKey, MultiDict[Key, Version]],
      snapshots: Map[Key, Version],
      data: Map[Version, Change[?]]
    )

    def empty: State = State(Info(Map.empty, Map.empty, Map.empty))

    extension (s: State)
      def getSnapshot(id: Key): Option[Version] =
        val p = State.unwrap(s)
        p.snapshots.get(id)

    extension (s: State)
      def setSnapshot(id: Key, version: Version): State =
        val p = State.unwrap(s)

        State(p.copy(snapshots = p.snapshots + (id -> version)))

    extension (s: State)
      def add[Event](id: Key, discriminator: Discriminator, event: Change[Event], catalog: Catalog): State =
        val p = State.unwrap(s)

        val r = p.events.updatedWith(EntryKey(catalog, discriminator)) {
          case None => MultiDict(id -> event.version).some
          case Some(map) => (map + (id -> event.version)).some

        }

        State(p.copy(events = r, data = p.data + (event.version -> event)))

    extension (s: State)
      def get[Event](version: Version, catalog: Catalog): Option[Change[Event]] =
        val p = State.unwrap(s)
        p.data.get(version).asInstanceOf[Option[Change[Event]]]

    extension (s: State)
      def fetch[Event](id: Key, discriminator: Discriminator, catalog: Catalog): Chunk[Change[Event]] =
        val p = State.unwrap(s)

        p.events.get(EntryKey(catalog, discriminator)) match {
          case None => Chunk()
          case Some(map) =>
            val versions = map.get(id)
            val changes = versions.toList.mapFilter { version => p.data.get(version) }
            Chunk.fromIterable(changes.asInstanceOf[List[Change[Event]]]).sorted
        }

    extension (s: State)
      def fetch[Event](
        id: Key,
        discriminator: Discriminator,
        snapshotVersion: Version,
        catalog: Catalog,
      ): Chunk[Change[Event]] =
        val p = s.fetch[Event](id, discriminator, catalog)
        p.filter(_.version.value > snapshotVersion.value)

    extension (s: State)
      def fetch[Event: MetaInfo](
        id: Option[Key],
        discriminator: Discriminator,
        query: PersistenceQuery,
        options: FetchOptions,
        catalog: Catalog,
      ): Chunk[Change[Event]] =
        val p = State.unwrap(s)

        p.events.get(EntryKey(catalog, discriminator)) match {
          case None => Chunk()
          case Some(map) =>
            val all = id match {
              case None =>
                p.data.values.asInstanceOf[Iterable[Change[Event]]]

              case Some(key) =>
                val versions = map.get(key)
                val changes = versions.toList.mapFilter { version => p.data.get(version) }
                changes.asInstanceOf[Iterable[Change[Event]]]
            }

            val matches = all.filter { ch =>
                query.isMatch(ch)
            }

            val ch = Chunk.fromIterable(matches)

            options match {
              case FetchOptions(Some(o), Some(l), order) =>

                order match {
                  case FetchOptions.Order.asc =>  ch.sorted.dropWhile(_.version.value <= o.value).take(l.toInt)
                  case FetchOptions.Order.desc => ch.sorted(using Ordering[Change[?]].reverse).dropWhile(_.version.value >= o.value).take(l.toInt)
                }

              case FetchOptions(Some(o), None, order)    =>

                order match {
                  case FetchOptions.Order.asc =>  ch.sorted.dropWhile(_.version.value <= o.value)
                  case FetchOptions.Order.desc => ch.sorted(using Ordering[Change[?]].reverse).dropWhile(_.version.value >= o.value)
                }

              case FetchOptions(None, Some(l), order)    =>

                order match {
                  case FetchOptions.Order.asc =>  ch.sorted.take(l.toInt)
                  case FetchOptions.Order.desc => ch.sorted(using Ordering[Change[?]].reverse).take(l.toInt)
                }

              case FetchOptions(None, None, order)       =>

                order match {
                  case FetchOptions.Order.asc =>  ch.sorted
                  case FetchOptions.Order.desc => ch.sorted(using Ordering[Change[?]].reverse)
                }
            }

        }

    extension (s: State)
      def fetch[Event: MetaInfo](
        id: Option[Key],
        discriminator: Discriminator,
        query: PersistenceQuery,
        interval: TimeInterval,
        catalog: Catalog,
      ): Chunk[Change[Event]] =
        val p = State.unwrap(s)

        p.events.get(EntryKey(catalog, discriminator)) match {
          case None => Chunk()
          case Some(map) =>
            val all = id match {
              case None =>
                p.data.values.asInstanceOf[Iterable[Change[Event]]]

              case Some(key) =>
                val versions = map.get(key)
                val changes = versions.toList.mapFilter { version => p.data.get(version) }
                changes.asInstanceOf[Iterable[Change[Event]]]
            }

            val matches = all.filter { ch =>
                query.isMatch(ch) && interval.contains(ch.payload.timestamp `getOrElse` ch.version.timestamp)
            }

            Chunk.fromIterable(matches)
        }

  }

  def live(): ZLayer[Any, Throwable, CQRSPersistence] =
    ZLayer {
      for {
        ref <- Ref.Synchronized.make(State.empty)

      } yield new MemoryCQRSPersistenceLive(ref)

    }

  class MemoryCQRSPersistenceLive(ref: Ref.Synchronized[State]) extends CQRSPersistence { self =>
    def readEvent[Event: {BinaryCodec, Tag, MetaInfo}](
      version: Version,
      catalog: Catalog,
      ): Task[Option[Change[Event]]] =
      ref.get.map(_.get(version, catalog))

    def readEvents[Event:{ BinaryCodec, Tag, MetaInfo}](
      id: Key,
      discriminator: Discriminator,
      catalog: Catalog,
    ): Task[Chunk[Change[Event]]] =
      ref.get.map(_.fetch(id, discriminator, catalog))

    def readEvents[Event:{ BinaryCodec, Tag, MetaInfo}](
      id: Key,
      discriminator: Discriminator,
      snapshotVersion: Version,
      catalog: Catalog,
    ): Task[Chunk[Change[Event]]] =
      ref.get.map(_.fetch(id, discriminator, snapshotVersion, catalog))

    def readEvents[Event:{ BinaryCodec, Tag, MetaInfo}](
      discriminator: Discriminator,
      query: PersistenceQuery,
      options: FetchOptions,
      catalog: Catalog,
    ): Task[Chunk[Change[Event]]] =
      ref.get.map(_.fetch(id = None, discriminator, query, options, catalog))

    def readEvents[Event:{ BinaryCodec, Tag, MetaInfo}](
      id: Key,
      discriminator: Discriminator,
      query: PersistenceQuery,
      options: FetchOptions,
      catalog: Catalog,
    ): Task[Chunk[Change[Event]]] =
      ref.get.map(_.fetch(id = id.some, discriminator, query, options, catalog))

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      id: Key,
      discriminator: Discriminator,
      query: PersistenceQuery,
      interval: TimeInterval,
      catalog: Catalog
    ): Task[Chunk[Change[Event]]] =
      ref.get.map(_.fetch(id = id.some, discriminator, query, interval, catalog))

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      discriminator: Discriminator,
      query: PersistenceQuery,
      interval: TimeInterval,
      catalog: Catalog,
      ): Task[Chunk[Change[Event]]] =
      ref.get.map(_.fetch(id = None, discriminator, query, interval, catalog))

    def saveEvent[Event: {BinaryCodec, MetaInfo, Tag}](
      id: Key,
      discriminator: Discriminator,
      event: Change[Event],
      catalog: Catalog,
    ): Task[Int] =
      ref.update(_.add(id, discriminator, event, catalog)) `as` 1

    def readSnapshot(id: Key): Task[Option[Version]] =
      ref.get.map(_.getSnapshot(id))

    def saveSnapshot(id: Key, version: Version): Task[Int] =
      ref.update(_.setSnapshot(id, version)) `as` 1

  }

}

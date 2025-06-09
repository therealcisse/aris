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


  type State = State.Type

  object State extends Newtype[State.Info] {
    case class EntryKey(catalog: Catalog, discriminator: Discriminator)

    case class Info(
      events: Map[EntryKey, MultiDict[Key, Version]],
      snapshots: Map[Key, Version],
      data: Map[Version, Change[?]],
      tags: Map[aris.Tag, Set[Version]]
    )

    def empty: State = State(Info(Map.empty, Map.empty, Map.empty, Map.empty))

    extension (s: State)
      def getSnapshot(id: Key): Option[Version] =
        val p = State.unwrap(s)
        p.snapshots.get(id)

    extension (s: State)
      def setSnapshot(id: Key, version: Version): State =
        val p = State.unwrap(s)

        State(p.copy(snapshots = p.snapshots + (id -> version)))

    extension (s: State)
      def add[Event: MetaInfo](id: Key, discriminator: Discriminator, event: Change[Event], catalog: Catalog): State =
        val p = State.unwrap(s)

        val r = p.events.updatedWith(EntryKey(catalog, discriminator)) {
          case None => MultiDict(id -> event.version).some
          case Some(map) => (map + (id -> event.version)).some

        }

        val tagMap = summon[MetaInfo[Event]]
          .tags(event.payload)
          .foldLeft(p.tags) { (acc, t) =>
            acc.updatedWith(t) {
              case None       => Some(Set(event.version))
              case Some(set)  => Some(set + event.version)
            }
          }

        State(p.copy(events = r, data = p.data + (event.version -> event), tags = tagMap))

    extension (s: State)
      def get[Event](version: Version, catalog: Catalog): Option[Change[Event]] =
        val p = State.unwrap(s)
        p.data.get(version).asInstanceOf[Option[Change[Event]]]

    extension (s: State)
      def fetch[Event: MetaInfo](id: Key, discriminator: Discriminator, tag: Option[aris.Tag], catalog: Catalog): Chunk[Change[Event]] =
        val p = State.unwrap(s)

        p.events.get(EntryKey(catalog, discriminator)) match {
          case None => Chunk()
          case Some(map) =>
            val versions = map.get(id)
            val changes = versions.toList.mapFilter { version => p.data.get(version) }
            val filtered = changes.asInstanceOf[List[Change[Event]]].filter(ch => tag.forall(t => p.tags.get(t).exists(_.contains(ch.version))))
            Chunk.fromIterable(filtered).sorted
        }

    extension (s: State)
      def fetch[Event: MetaInfo](
        id: Key,
        discriminator: Discriminator,
        snapshotVersion: Version,
        tag: Option[aris.Tag],
        catalog: Catalog,
      ): Chunk[Change[Event]] =
        val p = s.fetch[Event](id, discriminator, tag, catalog)
        p.filter(_.version.value > snapshotVersion.value)

    extension (s: State)
      def fetch[Event: MetaInfo](
        id: Option[Key],
        discriminator: Discriminator,
        namespace: Namespace,
        tag: Option[aris.Tag],
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
                ch.payload.namespace == namespace && tag.forall(t => p.tags.get(t).exists(_.contains(ch.version)))
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
        namespace: Namespace,
        tag: Option[aris.Tag],
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
                ch.payload.namespace == namespace && interval.contains(ch.payload.timestamp `getOrElse` ch.version.timestamp) &&
                  tag.forall(t => p.tags.get(t).exists(_.contains(ch.version)))
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
    tag: Option[aris.Tag],
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]] =
      ref.get.map(_.fetch(id, discriminator, tag, catalog))

  def readEvents[Event:{ BinaryCodec, Tag, MetaInfo}](
    id: Key,
    discriminator: Discriminator,
    snapshotVersion: Version,
    tag: Option[aris.Tag],
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]] =
      ref.get.map(_.fetch(id, discriminator, snapshotVersion, tag, catalog))

  def readEvents[Event:{ BinaryCodec, Tag, MetaInfo}](
    discriminator: Discriminator,
    namespace: Namespace,
    tag: Option[aris.Tag],
    options: FetchOptions,
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]] =
      ref.get.map(_.fetch(id = None, discriminator, namespace, tag, options, catalog))

  def readEvents[Event:{ BinaryCodec, Tag, MetaInfo}](
    id: Key,
    discriminator: Discriminator,
    namespace: Namespace,
    tag: Option[aris.Tag],
    options: FetchOptions,
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]] =
      ref.get.map(_.fetch(id = id.some, discriminator, namespace, tag, options, catalog))

  def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
    id: Key,
    discriminator: Discriminator,
    namespace: Namespace,
    tag: Option[aris.Tag],
    interval: TimeInterval,
    catalog: Catalog
  ): Task[Chunk[Change[Event]]] =
      ref.get.map(_.fetch(id = id.some, discriminator, namespace, tag, interval, catalog))

  def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
    discriminator: Discriminator,
    namespace: Namespace,
    tag: Option[aris.Tag],
    interval: TimeInterval,
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]] =
      ref.get.map(_.fetch(id = None, discriminator, namespace, tag, interval, catalog))

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

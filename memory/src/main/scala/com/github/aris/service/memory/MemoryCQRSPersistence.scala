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
      tags: Map[EvenTag, Set[Version]],
      namespaces: Map[Version, Namespace],
    )

    def empty: State = State(Info(Map.empty, Map.empty, Map.empty, Map.empty, Map.empty))

    extension (s: State)
      def getSnapshot(id: Key): Option[Version] =
        val p = State.unwrap(s)
        p.snapshots.get(id)

    extension (s: State)
      def setSnapshot(id: Key, version: Version): State =
        val p = State.unwrap(s)

        State(p.copy(snapshots = p.snapshots + (id -> version)))

    extension (s: State)
      def add[Event: MetaInfo](id: Key, discriminator: Discriminator, namespace: Namespace, event: Change[Event], catalog: Catalog): State =
        val p = State.unwrap(s)

        val r = p.events.updatedWith(EntryKey(catalog, discriminator)) {
          case None => MultiDict(id -> event.version).some
          case Some(map) => (map + (id -> event.version)).some

        }

        val tagMap = summon[MetaInfo[Event]]
          .tags(event.payload)
          .foldLeft(p.tags) { (acc, t) =>
            acc.updatedWith(t) {
              case None      => Some(Set(event.version))
              case Some(set) => Some(set + event.version)
            }
          }

        State(
          p.copy(
            events = r,
            data = p.data + (event.version -> event),
            tags = tagMap,
            namespaces = p.namespaces + (event.version -> namespace),
          )
        )

    extension (s: State)
      def get[Event](version: Version, catalog: Catalog): Option[Change[Event]] =
        val p = State.unwrap(s)
        p.data.get(version).asInstanceOf[Option[Change[Event]]]

    extension (s: State)
      def fetch[Event: MetaInfo](id: Key, tag: Option[EvenTag], catalog: Catalog): Chunk[Change[Event]] =
        val p = State.unwrap(s)

        val versions = p.events.collect {
          case (EntryKey(cat, _), map) if cat == catalog => map.get(id)
        }.foldLeft(Iterable.empty[Version])(_ ++ _)

        val changes = versions.toList.mapFilter(v => p.data.get(v))
        val filtered = changes.asInstanceOf[List[Change[Event]]].filter(ch => tag.forall(t => p.tags.get(t).exists(_.contains(ch.version))))
        Chunk.fromIterable(filtered).sorted

    extension (s: State)
      def fetch[Event: MetaInfo](
        id: Key,
        snapshotVersion: Version,
        tag: Option[EvenTag],
        catalog: Catalog,
      ): Chunk[Change[Event]] =
        val p = s.fetch[Event](id, tag, catalog)
        p.filter(_.version.value > snapshotVersion.value)

    extension (s: State)
      def fetch[Event: MetaInfo](
        id: Option[Key],
        namespace: Namespace,
        tag: Option[EvenTag],
        options: FetchOptions,
        catalog: Catalog,
      ): Chunk[Change[Event]] =
        val p = State.unwrap(s)

        val maps = p.events.collect { case (EntryKey(cat, _), map) if cat == catalog => map }

        val all = id match {
          case None =>
            maps.iterator.flatMap(_.iterator.map(_._2)).flatMap(v => p.data.get(v)).asInstanceOf[Iterable[Change[Event]]]

          case Some(key) =>
            maps.iterator.flatMap(_.get(key)).flatMap(v => p.data.get(v)).asInstanceOf[Iterable[Change[Event]]]
        }

        val matches = all.filter { ch =>
            p.namespaces.get(ch.version).contains(namespace) &&
              tag.forall(t => p.tags.get(t).exists(_.contains(ch.version)))
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
        namespace: Namespace,
        tag: Option[EvenTag],
        interval: TimeInterval,
        catalog: Catalog,
      ): Chunk[Change[Event]] =
        val p = State.unwrap(s)

        val maps = p.events.collect { case (EntryKey(cat, _), map) if cat == catalog => map }

        val all = id match {
          case None =>
            maps.iterator.flatMap(_.iterator.map(_._2)).flatMap(v => p.data.get(v)).asInstanceOf[Iterable[Change[Event]]]

          case Some(key) =>
            maps.iterator.flatMap(_.get(key)).flatMap(v => p.data.get(v)).asInstanceOf[Iterable[Change[Event]]]
        }

        val matches = all.filter { ch =>
            p.namespaces.get(ch.version).contains(namespace) &&
              interval.contains(ch.payload.timestamp `getOrElse` ch.version.timestamp) &&
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
    tag: Option[EvenTag],
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]] =
      ref.get.map(_.fetch(id, tag, catalog))

  def readEvents[Event:{ BinaryCodec, Tag, MetaInfo}](
    id: Key,
    snapshotVersion: Version,
    tag: Option[EvenTag],
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]] =
      ref.get.map(_.fetch(id, snapshotVersion, tag, catalog))

  def readEvents[Event:{ BinaryCodec, Tag, MetaInfo}](
    discriminator: Discriminator,
    namespace: Namespace,
    tag: Option[EvenTag],
    options: FetchOptions,
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]] =
      ref.get.map(_.fetch(id = None, discriminator, namespace, tag, options, catalog))

  def readEvents[Event:{ BinaryCodec, Tag, MetaInfo}](
    id: Key,
    namespace: Namespace,
    tag: Option[EvenTag],
    options: FetchOptions,
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]] =
      ref.get.map(_.fetch(id = id.some, namespace, tag, options, catalog))

  def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
    id: Key,
    namespace: Namespace,
    tag: Option[EvenTag],
    interval: TimeInterval,
    catalog: Catalog
  ): Task[Chunk[Change[Event]]] =
      ref.get.map(_.fetch(id = id.some, namespace, tag, interval, catalog))

  def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
    discriminator: Discriminator,
    namespace: Namespace,
    tag: Option[EvenTag],
    interval: TimeInterval,
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]] =
      ref.get.map(_.fetch(id = None, discriminator, namespace, tag, interval, catalog))

    def saveEvent[Event: {BinaryCodec, MetaInfo, Tag}](
      id: Key,
      discriminator: Discriminator,
      namespace: Namespace,
      event: Change[Event],
      catalog: Catalog,
    ): Task[Int] =
      ref.update(_.add(id, discriminator, namespace, event, catalog)) `as` 1

    def readSnapshot(id: Key): Task[Option[Version]] =
      ref.get.map(_.getSnapshot(id))

    def saveSnapshot(id: Key, version: Version): Task[Int] =
      ref.update(_.setSnapshot(id, version)) `as` 1

  }

}

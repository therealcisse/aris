package com.github
package aris
package service
package memory

import cats.implicits.*

import com.github.aris.domain.*
import zio.prelude.{Ordering as _, *}
import zio.schema.codec.*

import scala.collection.immutable.MultiDict


trait MemoryCQRSPersistence extends CQRSPersistence {}

object MemoryCQRSPersistence {

  import zio.*
  import zio.schema.*


  type State = State.Type

  object State extends Newtype[State.Info] {
    case class EntryKey(catalog: Catalog, discriminator: Discriminator, namespace: Namespace)

    case class Info(
                     events: Map[EntryKey, MultiDict[Key, Version]],
                     snapshots: Map[Key, Version],
                     data: Map[Version, Change[?]],
                     tags: Map[EventTag, Set[Version]],
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
      def add[Event: MetaInfo](id: Key, discriminator: Discriminator, namespace: Namespace, event: Change[Event], catalog: Catalog): State =
        val p = State.unwrap(s)

        val r = p.events.updatedWith(EntryKey(catalog, discriminator, namespace)) {
          case None => MultiDict(id -> event.version).some
          case Some(map) => (map + (id -> event.version)).some

        }

        val tagMap = summon[MetaInfo[Event]]
          .tags(event.payload)
          .foldLeft(p.tags) { (acc, t) =>
            acc.updatedWith(t) {
              case None => Some(Set(event.version))
              case Some(set) => Some(set + event.version)
            }
          }

        State(
          p.copy(
            events = r,
            data = p.data + (event.version -> event),
            tags = tagMap,
          )
        )

    extension (s: State)
      def get[Event](version: Version, catalog: Catalog): Option[Change[Event]] =
        val p = State.unwrap(s)
        p.data.get(version).asInstanceOf[Option[Change[Event]]]

  }

  def live(): ZLayer[Any, Throwable, CQRSPersistence] =
    ZLayer {
      for {
        ref <- Ref.Synchronized.make(State.empty)

      } yield new MemoryCQRSPersistenceLive(ref)

    }

  class MemoryCQRSPersistenceLive(ref: Ref.Synchronized[State]) extends CQRSPersistence {
    self =>
    def readEvent[Event: {BinaryCodec, Tag, MetaInfo}](
                                                        version: Version,
                                                        catalog: Catalog,
                                                      ): Task[Option[Change[Event]]] =
                                                        ref.get.map(_.get(version, catalog))

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
                                                        id: Key,
                                                        catalog: Catalog,
                                                      ): Task[Chunk[Change[Event]]] =
                                                        ref.get.map { state =>
                                                          val info = State.unwrap(state)
                                                          Chunk.fromIterable(
                                                            info.events.flatMap { case (_, multiDict) =>
                                                              multiDict.get(id).toList.mapFilter {
                                                                case version =>
                                                                  info.data.get(version).asInstanceOf[Option[Change[Event]]]
                                                              }
                                                            }
                                                          )
                                                        }

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
                                                        id: Key,
                                                        options: FetchOptions,
                                                        catalog: Catalog,
                                                      ): Task[Chunk[Change[Event]]] =
                                                        ref.get.map { state =>
                                                          val info = State.unwrap(state)
                                                          val events = info.events.flatMap { case (_, multiDict) =>
                                                            multiDict.get(id).flatMap(version =>
                                                              info.data.get(version).asInstanceOf[Option[Change[Event]]]
                                                            )
                                                          }

                                                          val orderedEvents = events.toSeq.sortBy(_.version.timestamp)(if options.order == FetchOptions.Order.asc then Ordering[Timestamp] else Ordering[Timestamp].reverse)
                                                          val offsetIndex = options.offset.flatMap(offset => orderedEvents.indexWhere(_.version == offset) match {
                                                            case -1 => None
                                                            case idx => Some(idx + 1)
                                                          }).getOrElse(0)

                                                          val slicedEvents = orderedEvents.slice(offsetIndex, options.limit.fold(orderedEvents.size)(_ + offsetIndex))
                                                        Chunk.fromArray(slicedEvents.toArray)
                                                      }

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
                                                        namespace: Namespace,
                                                        options: FetchOptions,
                                                        catalog: Catalog,
                                                        tag: EventTag,
                                                      ): Task[Chunk[Change[Event]]] =
                                                        ref.get.map { state =>
                                                          val info     = State.unwrap(state)
                                                          val versions = info.tags.getOrElse(tag, Set.empty)
                                                          val events = info.events.collect {
                                                            case (State.EntryKey(`catalog`, _, ns), multiDict) if ns == namespace =>
                                                              multiDict.collect { case (_, v) if versions.contains(v) => info.data.get(v).asInstanceOf[Option[Change[Event]]] }
                                                          }.flatten

                                                          val orderedEvents = events.toSeq.sortBy(_.version.timestamp)(if options.order == FetchOptions.Order.asc then Ordering[Timestamp] else Ordering[Timestamp].reverse)
                                                          val offsetIndex = options.offset.flatMap(offset => orderedEvents.indexWhere(_.version == offset) match {
                                                            case -1 => None
                                                            case idx => Some(idx + 1)
                                                          }).getOrElse(0)

                                                          val slicedEvents = orderedEvents.slice(offsetIndex, options.limit.fold(orderedEvents.size)(_ + offsetIndex))
                                                          Chunk.fromArray(slicedEvents.toArray)
                                                        }

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
                                                        discriminator: Discriminator,
                                                        namespace: Namespace,
                                                        options: FetchOptions,
                                                        catalog: Catalog,
                                                      ): Task[Chunk[Change[Event]]] =
                                                        ref.get.map { state =>
                                                          val info = State.unwrap(state)
                                                          val events = info.events.get(State.EntryKey(catalog, discriminator, namespace)).toSeq.flatMap { multiDict =>
                                                            multiDict.flatMap { case (_, version) =>
                                                              info.data.get(version).asInstanceOf[Option[Change[Event]]]
                                                            }
                                                          }

                                                          val orderedEvents = events.sortBy(_.version.timestamp)(if options.order == FetchOptions.Order.asc then Ordering[Timestamp] else Ordering[Timestamp].reverse)
                                                          val offsetIndex = options.offset.flatMap(offset => orderedEvents.indexWhere(_.version == offset) match {
                                                            case -1 => None
                                                            case idx => Some(idx + 1)
                                                          }).getOrElse(0)

                                                          val slicedEvents = orderedEvents.slice(offsetIndex, options.limit.fold(orderedEvents.size)(_ + offsetIndex))
                                                          Chunk.fromArray(slicedEvents.toArray)
                                                        }

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
                                                        id: Key,
                                                        interval: TimeInterval,
                                                        catalog: Catalog
                                                      ): Task[Chunk[Change[Event]]] =
                                                        ref.get.map { state =>
                                                          val info = State.unwrap(state)
                                                          Chunk.fromIterable(
                                                            info.events.flatMap {
                                                              case (entryKey, multiDict) if entryKey.catalog == catalog =>
                                                                multiDict.get(id).toList.mapFilter {
                                                                  case version if interval.contains(version.timestamp) =>
                                                                    info.data.get(version).asInstanceOf[Option[Change[Event]]]
                                                                  case _ => None
                                                                }

                                                              case _ => Nil
                                                            }
                                                          )
                                                        }

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
                                                        discriminator: Discriminator,
                                                        namespace: Namespace,
                                                        interval: TimeInterval,
                                                        catalog: Catalog,
                                                      ): Task[Chunk[Change[Event]]] =
                                                        ref.get.map { state =>
                                                          val info = State.unwrap(state)
                                                          Chunk.fromIterable(
                                                            info.events.get(State.EntryKey(catalog, discriminator, namespace)).fold(Nil) { multiDict =>
                                                                multiDict.flatMap {
                                                                  case (_, version) if interval.contains(version.timestamp) =>
                                                                    info.data.get(version).asInstanceOf[Option[Change[Event]]]
                                                                }
                                                            }
                                                          )
                                                        }

    def saveEvent[Event: {BinaryCodec, MetaInfo, Tag}](
                                                        id: Key,
                                                        discriminator: Discriminator,
                                                        namespace: Namespace,
                                                        event: Change[Event],
                                                        catalog: Catalog,
                                                      ): Task[Int] =
      ref.update { state =>
        val updatedState = state.add(id, discriminator, namespace, event, catalog)
        updatedState
      } `as` 1

    def readSnapshot(id: Key): Task[Option[Version]] =
      ref.get.map(_.getSnapshot(id))

    def saveSnapshot(id: Key, version: Version): Task[Int] =
      ref.update(_.setSnapshot(id, version)) `as` 1

  }

}

package com.github
package aris
package store

import com.github.aris.domain.*
import com.github.aris.service.CQRSPersistence

import zio.*
import zio.prelude.*

import zio.schema.codec.*

transparent trait EventStore[Event] {
  def readEvents(id: Key): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(id: Key, snapshotVersion: Version): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(options: FetchOptions): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(id: Key, options: FetchOptions): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(id: Key, interval: TimeInterval): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(interval: TimeInterval): Task[Option[NonEmptyList[Change[Event]]]]
  def readEvents(tag: EventTag, options: FetchOptions): Task[Option[NonEmptyList[Change[Event]]]]

  def save(id: Key, event: Change[Event]): Task[Long]
}

object EventStore {
  def apply[Event: { BinaryCodec, Tag, MetaInfo }](
    persistence: CQRSPersistence,
    discriminator: Discriminator,
    namespace: Namespace,
    catalog: Catalog = Catalog.Default,
  ): EventStore[Event] = new EventStore[Event] {
    private def toNel(ch: Chunk[Change[Event]]): Option[NonEmptyList[Change[Event]]] =
      NonEmptyList.fromIterableOption(ch)

    def readEvents(id: Key): Task[Option[NonEmptyList[Change[Event]]]] =
      persistence.readEvents[Event](id, catalog).map(toNel)

    def readEvents(id: Key, snapshotVersion: Version): Task[Option[NonEmptyList[Change[Event]]]] =
      persistence.readEvents[Event](id, FetchOptions().offset(snapshotVersion.asKey), catalog).map(toNel)

    def readEvents(options: FetchOptions): Task[Option[NonEmptyList[Change[Event]]]] =
      persistence.readEvents[Event](discriminator, namespace, options, catalog).map(toNel)

    def readEvents(id: Key, options: FetchOptions): Task[Option[NonEmptyList[Change[Event]]]] =
      persistence.readEvents[Event](id, options, catalog).map(toNel)

    def readEvents(id: Key, interval: TimeInterval): Task[Option[NonEmptyList[Change[Event]]]] =
      persistence.readEvents[Event](id, interval, catalog).map(toNel)

    def readEvents(interval: TimeInterval): Task[Option[NonEmptyList[Change[Event]]]] =
      persistence.readEvents[Event](discriminator, namespace, interval, catalog).map(toNel)

    def readEvents(tag: EventTag, options: FetchOptions): Task[Option[NonEmptyList[Change[Event]]]] =
      persistence.readEvents[Event](namespace, options, catalog, tag).map(toNel)

    def save(id: Key, event: Change[Event]): Task[Long] =
      persistence.saveEvent(id, discriminator, namespace, event, catalog).map(_.toLong)
  }
}

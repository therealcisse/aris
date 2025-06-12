package com.github
package aris
package tenants

import aris.*
import aris.store.EventStore
import aris.service.CQRSPersistence

import zio.*
import zio.prelude.*

trait TenantEventStore extends EventStore[TenantEvent]

object TenantEventStore {
  def live(
      catalog: Catalog = Catalog.Default,
  ): ZLayer[CQRSPersistence, Nothing, TenantEventStore] =
    ZLayer.fromFunction { (persistence: CQRSPersistence) =>
      new TenantEventStoreLive(persistence, catalog)
    }

  class TenantEventStoreLive(persistence: CQRSPersistence, catalog: Catalog)
      extends TenantEventStore {
    private val discriminator = Discriminator("Tenant")
    private val rootNamespace = Namespace.root

    private def toNel(
        ch: Chunk[Change[TenantEvent]]
    ): Option[NonEmptyList[Change[TenantEvent]]] =
      NonEmptyList.fromChunk(ch)

    def readEvents(id: Key): Task[Option[NonEmptyList[Change[TenantEvent]]]] =
      persistence
        .readEvents[TenantEvent](id, None, catalog)
        .map(toNel)

    def readEvents(
        id: Key,
        snapshotVersion: Version,
    ): Task[Option[NonEmptyList[Change[TenantEvent]]]] =
      persistence
        .readEvents[TenantEvent](id, snapshotVersion, None, catalog)
        .map(toNel)

    def readEvents(options: FetchOptions): Task[Option[NonEmptyList[Change[TenantEvent]]]] =
      persistence
        .readEvents[TenantEvent](discriminator, rootNamespace, None, options, catalog)
        .map(toNel)

    def readEvents(id: Key, options: FetchOptions): Task[Option[NonEmptyList[Change[TenantEvent]]]] =
      persistence
        .readEvents[TenantEvent](id, None, options, catalog)
        .map(toNel)

    def readEvents(id: Key, interval: TimeInterval): Task[Option[NonEmptyList[Change[TenantEvent]]]] =
      persistence
        .readEvents[TenantEvent](id, None, interval, catalog)
        .map(toNel)

    def readEvents(interval: TimeInterval): Task[Option[NonEmptyList[Change[TenantEvent]]]] =
      persistence
        .readEvents[TenantEvent](discriminator, rootNamespace, None, interval, catalog)
        .map(toNel)

    def save(id: Key, event: Change[TenantEvent]): Task[Long] =
      persistence.saveEvent(id, discriminator, rootNamespace, event, catalog).map(_.toLong)
  }
}

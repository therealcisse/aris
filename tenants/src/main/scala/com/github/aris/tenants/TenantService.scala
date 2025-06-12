package com.github
package aris
package tenants

import com.github.aris.*
import com.github.aris.service.CQRSPersistence
import com.github.aris.domain.*
import com.github.aris.store.*

import zio.*
import zio.prelude.*

trait TenantService {
  def addTenant(id: Namespace, name: String, description: String, ts: Timestamp): Task[Unit]
  def deleteTenant(id: Namespace, ts: Timestamp): Task[Unit]
  def enableTenant(id: Namespace, ts: Timestamp): Task[Unit]
  def disableTenant(id: Namespace, ts: Timestamp): Task[Unit]

  def loadTenant(id: Namespace): Task[Option[NameTenant]]
  def loadTenants(options: FetchOptions): Task[Chunk[NameTenant]]
  def loadEvents(id: Namespace): Task[Option[NonEmptyList[Change[TenantEvent]]]]
}

object TenantService {
  def live(catalog: Catalog = Catalog.Default): ZLayer[CQRSPersistence, Nothing, TenantService] =
    ZLayer.fromZIO {
      for {
        persistence <- ZIO.service[CQRSPersistence]
      } yield TenantServiceLive(persistence, catalog)
    }

  case class TenantServiceLive(persistence: CQRSPersistence, catalog: Catalog) extends TenantService {
    private val discriminator = Discriminator("Tenant")
    private val rootNamespace = Namespace.root

    private val store = EventStore[TenantEvent](persistence, discriminator, rootNamespace, catalog)

    private val events = CQRS[TenantEvent, TenantCommand](store)

    def addTenant(id: Namespace, name: String, description: String, ts: Timestamp): Task[Unit] =
      events.add(id.asKey, TenantCommand.AddTenant(id, name, description, ts))

    def deleteTenant(id: Namespace, ts: Timestamp): Task[Unit] =
      events.add(id.asKey, TenantCommand.DeleteTenant(id, ts))

    def enableTenant(id: Namespace, ts: Timestamp): Task[Unit] =
      events.add(id.asKey, TenantCommand.EnableTenant(id, ts))

    def disableTenant(id: Namespace, ts: Timestamp): Task[Unit] =
      events.add(id.asKey, TenantCommand.DisableTenant(id, ts))

    def loadTenant(id: Namespace): Task[Option[NameTenant]] =
      store.readEvents(id.asKey).map(_.flatMap(NameTenantEventHandler.applyEvents))

    def loadTenants(options: FetchOptions): Task[Chunk[NameTenant]] =
      store.readEvents(options).map {
        case Some(events) =>
          NameTenantsEventHandler.applyEvents(events).fold(Chunk.empty)(_.toChunk)
        case None => Chunk.empty
      }

    def loadEvents(id: Namespace): Task[Option[NonEmptyList[Change[TenantEvent]]]] =
      store.readEvents(id.asKey)
  }
}

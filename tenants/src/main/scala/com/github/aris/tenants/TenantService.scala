package com.github
package aris
package tenants

import aris.*
import aris.store.*

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
  def live(): ZLayer[TenantCQRS with TenantEventStore, Nothing, TenantService] =
    ZLayer.fromFunction(new TenantServiceLive(_, _))

  class TenantServiceLive(cqrs: TenantCQRS, store: TenantEventStore) extends TenantService {
    def addTenant(id: Namespace, name: String, description: String, ts: Timestamp): Task[Unit] =
      cqrs.add(id.asKey, TenantCommand.AddTenant(id, name, description, ts))

    def deleteTenant(id: Namespace, ts: Timestamp): Task[Unit] =
      cqrs.add(id.asKey, TenantCommand.DeleteTenant(id, ts))

    def enableTenant(id: Namespace, ts: Timestamp): Task[Unit] =
      cqrs.add(id.asKey, TenantCommand.EnableTenant(id, ts))

    def disableTenant(id: Namespace, ts: Timestamp): Task[Unit] =
      cqrs.add(id.asKey, TenantCommand.DisableTenant(id, ts))

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

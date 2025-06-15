package com.github
package aris
package tenants

import com.github.aris.*

import zio.*
import doobie.*
import doobie.implicits.*

trait TenantRepository {
  def add(id: TenantId, namespace: Namespace, name: String, description: String, created: Timestamp): Task[Unit]
  def delete(id: TenantId): Task[Unit]
  def disable(id: TenantId): Task[Unit]
  def enable(id: TenantId): Task[Unit]
}

object TenantRepository {
  def live(xa: Transactor[Task]): ZLayer[Any, Nothing, TenantRepository] =
    ZLayer.succeed(new DoobieTenantRepository(xa))

  final class DoobieTenantRepository(xa: Transactor[Task]) extends TenantRepository {
    private val rootNamespace = Namespace.root

    def add(id: TenantId, namespace: Namespace, name: String, description: String, created: Timestamp): Task[Unit] =
      sql"""INSERT INTO tenants(namespace, id, name, description, created, status)
             VALUES ($namespace, $id, $name, $description, $created, 'active')
             ON CONFLICT(namespace, id) DO UPDATE SET name = EXCLUDED.name,
             description = EXCLUDED.description,
             created = EXCLUDED.created,
             status = 'active'""".update.run.transact(xa).unit

    def delete(id: TenantId): Task[Unit] =
      sql"DELETE FROM tenants WHERE namespace = $rootNamespace AND id = $id".update.run.transact(xa).unit

    def disable(id: TenantId): Task[Unit] =
      sql"UPDATE tenants SET status = 'disabled' WHERE namespace = $rootNamespace AND id = $id".update.run.transact(xa).unit

    def enable(id: TenantId): Task[Unit] =
      sql"UPDATE tenants SET status = 'active' WHERE namespace = $rootNamespace AND id = $id".update.run.transact(xa).unit
  }

  object memory {
    def live(): ZLayer[Any, Nothing, TenantRepository] =
      ZLayer.fromZIO(Ref.Synchronized.make(Map.empty[TenantId, String]).map { ref =>
        new TenantRepository {
          private val rootNamespace = Namespace.root

          def add(id: TenantId, namespace: Namespace, name: String, description: String, created: Timestamp): Task[Unit] =
            ref.update(_.updated(id, "active")).unit

          def delete(id: TenantId): Task[Unit] =
            ref.update(_ - id).unit

          def disable(id: TenantId): Task[Unit] =
            ref.update(_.updatedWith(id)(_.map(_ => "disabled"))).unit

          def enable(id: TenantId): Task[Unit] =
            ref.update(_.updatedWith(id)(_.map(_ => "active"))).unit
        }
      })
  }
}

package com.github
package aris
package tenants

import com.github.aris.*

import zio.*
import doobie.*
import doobie.implicits.*

trait TenantRepository {
  def add(id: TenantId, namespace: Namespace, name: String, description: String, created: Timestamp): Task[Unit]
  def delete(id: TenantId, namespace: Namespace): Task[Unit]
  def disable(id: TenantId, namespace: Namespace): Task[Unit]
  def enable(id: TenantId, namespace: Namespace): Task[Unit]
}

object TenantRepository {
  def live(xa: Transactor[Task]): ZLayer[Any, Nothing, TenantRepository] =
    ZLayer.succeed(new DoobieTenantRepository(xa))

  final class DoobieTenantRepository(xa: Transactor[Task]) extends TenantRepository {
    def add(id: TenantId, namespace: Namespace, name: String, description: String, created: Timestamp): Task[Unit] =
      sql"""INSERT INTO tenants(namespace, id, name, description, created, status)
             VALUES ($namespace, $id, $name, $description, $created, 'active')
             ON CONFLICT(namespace, id) DO UPDATE SET name = EXCLUDED.name,
             description = EXCLUDED.description,
             created = EXCLUDED.created,
             status = 'active'""".update.run.transact(xa).unit

    def delete(id: TenantId, namespace: Namespace): Task[Unit] =
      sql"DELETE FROM tenants WHERE namespace = $namespace AND id = $id".update.run.transact(xa).unit

    def disable(id: TenantId, namespace: Namespace): Task[Unit] =
      sql"UPDATE tenants SET status = 'disabled' WHERE namespace = $namespace AND id = $id".update.run.transact(xa).unit

    def enable(id: TenantId, namespace: Namespace): Task[Unit] =
      sql"UPDATE tenants SET status = 'active' WHERE namespace = $namespace AND id = $id".update.run.transact(xa).unit
  }
}

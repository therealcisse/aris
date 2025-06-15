package com.github
package aris
package tenants

import com.github.aris.*

import zio.*
import doobie.*
import doobie.implicits.*

trait TenantRepository {
  def add(id: Namespace, name: String, description: String, created: Timestamp): Task[Unit]
  def delete(id: Namespace): Task[Unit]
  def disable(id: Namespace): Task[Unit]
  def enable(id: Namespace): Task[Unit]
}

object TenantRepository {
  def live(xa: Transactor[Task]): ZLayer[Any, Nothing, TenantRepository] =
    ZLayer.succeed(new DoobieTenantRepository(xa))

  final class DoobieTenantRepository(xa: Transactor[Task]) extends TenantRepository {
    def add(id: Namespace, name: String, description: String, created: Timestamp): Task[Unit] =
      sql"""INSERT INTO tenants(id, name, description, created, status)
             VALUES ($id, $name, $description, $created, 'active')
             ON CONFLICT(id) DO UPDATE SET name = EXCLUDED.name,
             description = EXCLUDED.description,
             created = EXCLUDED.created,
             status = 'active'""".update.run.transact(xa).unit

    def delete(id: Namespace): Task[Unit] =
      sql"DELETE FROM tenants WHERE id = $id".update.run.transact(xa).unit

    def disable(id: Namespace): Task[Unit] =
      sql"UPDATE tenants SET status = 'disabled' WHERE id = $id".update.run.transact(xa).unit

    def enable(id: Namespace): Task[Unit] =
      sql"UPDATE tenants SET status = 'active' WHERE id = $id".update.run.transact(xa).unit
  }
}

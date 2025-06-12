package com.github
package aris
package projection
package postgres

import com.github.aris.projection.{Projection, ProjectionManagementStore}
import com.github.aris.Version
import zio.*
import doobie.*
import doobie.implicits.*

trait PostgresProjectionManagementStore extends ProjectionManagementStore

object PostgresProjectionManagementStore {
  def live(xa: Transactor[Task]): ZLayer[Any, Nothing, ProjectionManagementStore] =
    ZLayer.succeed(new PostgresProjectionManagementStoreLive(xa))

  class PostgresProjectionManagementStoreLive(xa: Transactor[Task])
      extends PostgresProjectionManagementStore
      with JdbcCodecs {
    def stop(id: Projection.Id): Task[Unit] =
      Queries.STOP(id).run.transact(xa).unit

    def resume(id: Projection.Id): Task[Unit] =
      Queries.RESUME(id).run.transact(xa).unit

    def isStopped(id: Projection.Id): Task[Boolean] =
      Queries.IS_STOPPED(id).option.transact(xa).map(_.getOrElse(false))

    def offset(id: Projection.Id): Task[Option[Version]] =
      Queries.READ_OFFSET(id).option.transact(xa)

    def updateOffset(id: Projection.Id, version: Version): Task[Unit] =
      Queries.UPDATE_OFFSET(id, version).run.transact(xa).unit
  }

  object Queries extends JdbcCodecs {
    def STOP(id: Projection.Id): Update0 =
      sql"""INSERT INTO projection_management (name, version, namespace, stopped)
             VALUES (${id.name}, ${id.version}, ${id.namespace}, true)
             ON CONFLICT (name, version, namespace) DO UPDATE SET stopped = true""".update

    def RESUME(id: Projection.Id): Update0 =
      sql"""INSERT INTO projection_management (name, version, namespace, stopped)
             VALUES (${id.name}, ${id.version}, ${id.namespace}, false)
             ON CONFLICT (name, version, namespace) DO UPDATE SET stopped = false""".update

    def IS_STOPPED(id: Projection.Id): Query0[Boolean] =
      sql"""SELECT stopped FROM projection_management WHERE name = ${id.name} AND version = ${id.version} AND namespace = ${id.namespace}"""
        .query[Boolean]

    def READ_OFFSET(id: Projection.Id): Query0[Version] =
      sql"""SELECT offset FROM projection_offset WHERE name = ${id.name} AND version = ${id.version} AND namespace = ${id.namespace}"""
        .query[Version]

    def UPDATE_OFFSET(id: Projection.Id, version: Version): Update0 =
      sql"""INSERT INTO projection_offset (name, version, namespace, offset)
             VALUES (${id.name}, ${id.version}, ${id.namespace}, $version)
             ON CONFLICT (name, version, namespace) DO UPDATE SET offset = $version""".update
  }
}

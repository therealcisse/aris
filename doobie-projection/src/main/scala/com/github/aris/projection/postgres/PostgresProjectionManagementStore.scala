package com.github
package aris
package projection
package postgres

import com.github.aris.projection.{ProjectionManagementStore, Projection}
import com.github.aris.Version
import zio.*
import doobie.*
import doobie.implicits.*

trait PostgresProjectionManagementStore extends ProjectionManagementStore

object PostgresProjectionManagementStore {
  def live(xa: Transactor[Task]): ZLayer[Any, Nothing, ProjectionManagementStore] =
    ZLayer.succeed(new PostgresProjectionManagementStoreLive(xa))

  class PostgresProjectionManagementStoreLive(xa: Transactor[Task]) extends PostgresProjectionManagementStore with JdbcCodecs {
    def pause(id: Projection.Id): Task[Unit] =
      Queries.PAUSE(id).run.transact(xa).unit

    def resume(id: Projection.Id): Task[Unit] =
      Queries.RESUME(id).run.transact(xa).unit

    def isPaused(id: Projection.Id): Task[Boolean] =
      Queries.IS_PAUSED(id).option.transact(xa).map(_.getOrElse(false))

    def offset(id: Projection.Id): Task[Option[Version]] =
      Queries.READ_OFFSET(id).option.transact(xa)

    def updateOffset(id: Projection.Id, version: Version): Task[Unit] =
      Queries.UPDATE_OFFSET(id, version).run.transact(xa).unit
  }

  object Queries extends JdbcCodecs {
    def PAUSE(id: Projection.Id): Update0 =
      sql"""INSERT INTO projections (name, version, offset, paused)
             VALUES (${id.name}, ${id.version}, 0, true)
             ON CONFLICT (name, version) DO UPDATE SET paused = true""".update

    def RESUME(id: Projection.Id): Update0 =
      sql"""INSERT INTO projections (name, version, offset, paused)
             VALUES (${id.name}, ${id.version}, 0, false)
             ON CONFLICT (name, version) DO UPDATE SET paused = false""".update

    def IS_PAUSED(id: Projection.Id): Query0[Boolean] =
      sql"""SELECT paused FROM projections WHERE name = ${id.name} AND version = ${id.version}""".query[Boolean]

    def READ_OFFSET(id: Projection.Id): Query0[Version] =
      sql"""SELECT offset FROM projections WHERE name = ${id.name} AND version = ${id.version}""".query[Version]

    def UPDATE_OFFSET(id: Projection.Id, version: Version): Update0 =
      sql"""INSERT INTO projections (name, version, offset, paused)
             VALUES (${id.name}, ${id.version}, $version, false)
             ON CONFLICT (name, version) DO UPDATE SET offset = $version""".update
  }
}

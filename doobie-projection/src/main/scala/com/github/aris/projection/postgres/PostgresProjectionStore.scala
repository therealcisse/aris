package com.github
package aris
package projection
package postgres

import com.github.aris.projection.{Projection, ProjectionStore}
import com.github.aris.Version
import zio.*
import doobie.*
import doobie.implicits.*

trait PostgresProjectionStore extends ProjectionStore

object PostgresProjectionStore {
  def live(xa: Transactor[Task]): ZLayer[Any, Nothing, ProjectionStore] =
    ZLayer.succeed(new PostgresProjectionStoreLive(xa))

  class PostgresProjectionStoreLive(xa: Transactor[Task]) extends PostgresProjectionStore with JdbcCodecs {
    def read(id: Projection.Id): Task[Option[Version]] =
      Queries.READ(id).option.transact(xa)

    def save(id: Projection.Id, version: Version): Task[Int] =
      Queries.SAVE(id, version).run.transact(xa).map(_.toInt)
  }

  object Queries extends JdbcCodecs {
    def READ(id: Projection.Id): Query0[Version] =
      sql"""SELECT offset FROM projection_offset WHERE name = ${id.name} AND version = ${id.version} AND namespace = ${id.namespace}"""
        .query[Version]

    def SAVE(id: Projection.Id, version: Version): Update0 =
      sql"""INSERT INTO projection_offset (name, version, namespace, offset)
             VALUES (${id.name}, ${id.version}, ${id.namespace}, $version)
             ON CONFLICT (name, version, namespace) DO UPDATE SET offset = $version""".update
  }
}

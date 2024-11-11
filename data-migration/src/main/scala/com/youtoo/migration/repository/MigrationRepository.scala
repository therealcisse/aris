package com.youtoo
package migration
package repository

import com.youtoo.migration.model.*

import zio.telemetry.opentelemetry.tracing.Tracing

import com.youtoo.cqrs.service.postgres.*

import zio.*
import zio.schema.*
import zio.schema.codec.*

import zio.jdbc.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.service.*

import com.youtoo.cqrs.Codecs.given

trait MigrationRepository {
  def load(id: Migration.Id): ZIO[ZConnection, Throwable, Option[Migration]]
  def loadMany(offset: Option[Key], limit: Long): ZIO[ZConnection, Throwable, Chunk[Key]]
  def save(o: Migration): ZIO[ZConnection, Throwable, Long]

}

object MigrationRepository {
  inline def load(id: Migration.Id): RIO[MigrationRepository & ZConnection, Option[Migration]] =
    ZIO.serviceWithZIO[MigrationRepository](_.load(id))

  inline def loadMany(offset: Option[Key], limit: Long): RIO[MigrationRepository & ZConnection, Chunk[Key]] =
    ZIO.serviceWithZIO[MigrationRepository](_.loadMany(offset, limit))

  inline def save(o: Migration): RIO[MigrationRepository & ZConnection, Long] =
    ZIO.serviceWithZIO[MigrationRepository](_.save(o))

  def live(): ZLayer[Tracing, Throwable, MigrationRepository] =
    ZLayer.fromFunction { (tracing: Tracing) =>
      new MigrationRepositoryLive().traced(tracing)
    }

  class MigrationRepositoryLive() extends MigrationRepository { self =>
    def load(id: Migration.Id): ZIO[ZConnection, Throwable, Option[Migration]] =
      Queries
        .READ_MIGRATION(id)
        .selectOne

    def loadMany(offset: Option[Key], limit: Long): ZIO[ZConnection, Throwable, Chunk[Key]] =
      Queries
        .READ_MIGRATIONS(offset, limit)
        .selectAll

    def save(o: Migration): ZIO[ZConnection, Throwable, Long] =
      Queries
        .SAVE_MIGRATION(o)
        .insert

    inline def traced(tracing: Tracing): MigrationRepository =
      new MigrationRepository {
        def load(id: Migration.Id): ZIO[ZConnection, Throwable, Option[Migration]] =
          self.load(id) @@ tracing.aspects.span("MigrationRepository.load")
        def loadMany(offset: Option[Key], limit: Long): ZIO[ZConnection, Throwable, Chunk[Key]] =
          self.loadMany(offset, limit) @@ tracing.aspects.span("MigrationRepository.loadMany")
        def save(o: Migration): ZIO[ZConnection, Throwable, Long] =
          self.save(o) @@ tracing.aspects.span("MigrationRepository.save")

      }

  }

  object Queries extends JdbcCodecs {
    given JdbcDecoder[Migration.State] = byteArrayDecoder[Migration.State]

    given JdbcDecoder[Migration.Id] = JdbcDecoder[Long].map(s => Migration.Id(Key(s)))

    given SqlFragment.Setter[Migration.Id] = SqlFragment.Setter[Long].contramap(_.asKey.value)

    inline def READ_MIGRATION(id: Migration.Id): Query[Migration] =
      sql"""
      SELECT id, state, timestamp
      FROM migrations
      WHERE id = $id
      """.query[(Migration.Id, Migration.State, Timestamp)].map(Migration.apply)

    inline def READ_MIGRATIONS(offset: Option[Key], limit: Long): Query[Key] =
      offset match {
        case None =>
          sql"""
          SELECT id
          FROM migrations
          ORDER BY id DESC
          """.query[Key]

        case Some(key) =>
          sql"""
          SELECT id
          FROM migrations
          WHERE id < $key
          ORDER BY id DESC
          LIMIT $limit
          """.query[Key]

      }

    inline def SAVE_MIGRATION(o: Migration): SqlFragment =
      val payload =
        java.util.Base64.getEncoder.encodeToString(summon[BinaryCodec[Migration.State]].encode(o.state).toArray)

      sql"""
      INSERT INTO migrations (id, state, timestamp)
      VALUES (${o.id}, decode(${payload}, 'base64'), ${o.timestamp})
      ON CONFLICT (id) DO UPDATE
      SET state = decode(${payload}, 'base64')
      """
  }
}

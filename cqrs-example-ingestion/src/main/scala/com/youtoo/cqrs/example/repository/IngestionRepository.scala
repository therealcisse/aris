package com.youtoo.cqrs
package example
package repository

import com.youtoo.cqrs.example.model.*
import com.youtoo.cqrs.service.postgres.*

import zio.*
import zio.schema.*
import zio.schema.codec.*

import zio.jdbc.*
import com.youtoo.cqrs.service.*

import com.youtoo.cqrs.Codecs.given

trait IngestionRepository {
  def load(id: Ingestion.Id): ZIO[ZConnection, Throwable, Option[Ingestion]]
  def loadMany(offset: Option[Key], limit: Long): ZIO[ZConnection, Throwable, Chunk[Key]]
  def save(o: Ingestion): ZIO[ZConnection, Throwable, Long]

}

object IngestionRepository {

  def live(): ZLayer[Any, Throwable, IngestionRepository] =
    ZLayer.succeed {

      new IngestionRepository {
        def load(id: Ingestion.Id): ZIO[ZConnection, Throwable, Option[Ingestion]] =
          Queries
            .READ_INGESTION(id)
            .selectOne

        def loadMany(offset: Option[Key], limit: Long): ZIO[ZConnection, Throwable, Chunk[Key]] =
          Queries
            .READ_INGESTIONS(offset, limit)
            .selectAll

        def save(o: Ingestion): ZIO[ZConnection, Throwable, Long] =
          Queries
            .SAVE_INGESTION(o)
            .insert

      }
    }

  object Queries extends JdbcCodecs {
    given JdbcDecoder[Ingestion.Status] = byteArrayDecoder[Ingestion.Status]
    given JdbcDecoder[Ingestion.Id] = JdbcDecoder[String].map(string => Ingestion.Id(Key(string)))
    given JdbcDecoder[Key] = JdbcDecoder[String].map(string => Key(string))
    given JdbcDecoder[Timestamp] = JdbcDecoder[Long].map(long => Timestamp(long))

    given SqlFragment.Setter[Ingestion.Id] = SqlFragment.Setter[String].contramap(_.asKey.value)

    inline def READ_INGESTION(id: Ingestion.Id): Query[Ingestion] =
      sql"""
      SELECT id, status, timestamp
      FROM ingestions
      WHERE id = $id
      """.query[(Ingestion.Id, Ingestion.Status, Timestamp)].map(Ingestion.apply)

    inline def READ_INGESTIONS(offset: Option[Key], limit: Long): Query[Key] =
      offset match {
        case None =>
          sql"""
          SELECT id
          FROM ingestions
          ORDER BY id DESC
          """.query[Key]

        case Some(key) =>
          sql"""
          SELECT id
          FROM ingestions
          WHERE id < $key
          ORDER BY id DESC
          LIMIT $limit
          """.query[Key]

      }

    inline def SAVE_INGESTION(o: Ingestion): SqlFragment =
      val payload =
        java.util.Base64.getEncoder.encodeToString(summon[BinaryCodec[Ingestion.Status]].encode(o.status).toArray)

      sql"""
      INSERT INTO ingestions (id, status, timestamp)
      VALUES (${o.id}, decode(${payload}, 'base64'), ${o.timestamp.value})
      ON CONFLICT (id) DO UPDATE
      SET status = decode(${payload}, 'base64'), timestamp = ${o.timestamp.value}
      """
  }
}

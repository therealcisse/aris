package com.youtoo.cqrs
package example
package repository

import com.youtoo.cqrs.example.model.*
import com.youtoo.cqrs.service.postgres.*

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.store.*

import zio.*
import zio.prelude.*
import zio.schema.*
import zio.schema.codec.*

import zio.jdbc.*
import com.youtoo.cqrs.service.*

import com.youtoo.cqrs.Codecs.*

trait IngestionRepository {
  def load(id: Ingestion.Id): ZIO[ZConnection, Throwable, Option[Ingestion]]
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

        def save(o: Ingestion): ZIO[ZConnection, Throwable, Long] =
          Queries
            .SAVE_INGESTION(o)
            .insert

      }
    }

  object Queries extends JdbcCodecs {
    given SqlFragment.Setter[Ingestion.Id] = SqlFragment.Setter[String].contramap(_.asKey.value)

    inline def READ_INGESTION(id: Ingestion.Id): Query[Ingestion] =
      sql"""
      SELECT id, status, timestamp
      FROM ingestions
      WHERE id = $id
      """.query[(Ingestion.Id, Ingestion.Status, Timestamp)].map { case (id, status, timestamp) =>
        Ingestion(id, status, timestamp)
      }

    inline def SAVE_INGESTION(o: Ingestion): SqlFragment =
      sql"""
      INSERT INTO ingestions (id, status, timestamp)
      VALUES (${o.id}, ${summon[BinaryCodec[Ingestion.Status]].encode(o.status)}, ${o.timestamp.value})
      ON CONFLICT (id) DO UPDATE
      SET status = ${summon[BinaryCodec[Ingestion.Status]].encode(o.status)}, timestamp = ${o.timestamp.value}
      """
  }
}

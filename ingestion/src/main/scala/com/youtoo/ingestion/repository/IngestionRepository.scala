package com.youtoo
package ingestion
package repository

import com.youtoo.ingestion.model.*
import com.youtoo.cqrs.service.postgres.*

import zio.*
import zio.schema.*
import zio.schema.codec.*

import zio.jdbc.*
import com.youtoo.cqrs.service.*

import com.youtoo.cqrs.Codecs.given

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*

trait IngestionRepository {
  def load(id: Ingestion.Id): ZIO[ZConnection, Throwable, Option[Ingestion]]
  def loadMany(offset: Option[Key], limit: Long): ZIO[ZConnection, Throwable, Chunk[Key]]
  def save(o: Ingestion): ZIO[ZConnection, Throwable, Long]

}

object IngestionRepository {
  inline def load(id: Ingestion.Id): RIO[IngestionRepository & ZConnection, Option[Ingestion]] =
    ZIO.serviceWithZIO[IngestionRepository](_.load(id))

  inline def loadMany(offset: Option[Key], limit: Long): RIO[IngestionRepository & ZConnection, Chunk[Key]] =
    ZIO.serviceWithZIO[IngestionRepository](_.loadMany(offset, limit))

  inline def save(o: Ingestion): RIO[IngestionRepository & ZConnection, Long] =
    ZIO.serviceWithZIO[IngestionRepository](_.save(o))

  def live(): ZLayer[Tracing, Throwable, IngestionRepository] =
    ZLayer.fromFunction(new IngestionRepositoryLive().traced(_))

  class IngestionRepositoryLive() extends IngestionRepository { self =>
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

    def traced(tracing: Tracing): IngestionRepository =
      new IngestionRepository {
        def load(id: Ingestion.Id): ZIO[ZConnection, Throwable, Option[Ingestion]] =
          self.load(id) @@ tracing.aspects.span(
            "IngestionRepository.load",
            attributes = Attributes(Attribute.long("jobId", id.asKey.value)),
          )
        def loadMany(offset: Option[Key], limit: Long): ZIO[ZConnection, Throwable, Chunk[Key]] =
          self.loadMany(offset, limit) @@ tracing.aspects.span(
            "IngestionRepository.loadMany",
            attributes = Attributes(
              Attribute.long("offset", offset.map(_.value).getOrElse(0L)),
              Attribute.long("limit", limit),
            ),
          )
        def save(o: Ingestion): ZIO[ZConnection, Throwable, Long] =
          self.save(o) @@ tracing.aspects.span(
            "IngestionRepository.save",
            attributes = Attributes(Attribute.long("jobId", o.id.asKey.value)),
          )

      }

  }

  object Queries extends JdbcCodecs {
    given JdbcDecoder[Ingestion.Status] = byteArrayDecoder[Ingestion.Status]

    given JdbcDecoder[Ingestion.Id] = JdbcDecoder[Long].map(n => Ingestion.Id(Key(n)))

    given SqlFragment.Setter[Ingestion.Id] = SqlFragment.Setter[Long].contramap(_.asKey.value)

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
          LIMIT $limit
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
      VALUES (${o.id}, decode(${payload}, 'base64'), ${o.timestamp})
      """

  }

}

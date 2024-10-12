package com.youtoo.cqrs
package service
package postgres

import com.youtoo.cqrs.domain.*

import zio.*

import zio.schema.codec.*
import zio.schema.codec.ProtobufCodec.*

import io.github.thibaultmeyer.cuid.CUID

trait PostgresCQRSPersistence extends CQRSPersistence {
}

object PostgresCQRSPersistence {
  import zio.*
  import zio.jdbc.*
  import zio.schema.*

  given [T: BinaryCodec.BinaryDecoder]: JdbcDecoder[T] =
    JdbcDecoder.byteArrayDecoder.map(array => summon[BinaryCodec.BinaryDecoder[T]].decode(Chunk(array*)).getOrElse(throw IllegalArgumentException("Can't decode array")))

  given [T: BinaryCodec.BinaryEncoder]: JdbcEncoder[T] =
    JdbcEncoder.byteChunkEncoder.contramap(t => summon[BinaryCodec.BinaryEncoder[T]].encode(t))

  def live(): ZLayer[ZConnectionPool, Throwable, CQRSPersistence] =
    ZLayer.fromFunction { (pool: ZConnectionPool) =>
      new CQRSPersistence {

        def readAggregate(id: Key): Task[Option[Aggregate]] =
          transaction {
            Queries.READ_AGGREGATE(id).selectOne
          }.provideEnvironment(ZEnvironment(pool))

        def saveAggregate(agg: Aggregate): Task[Unit] =
          transaction {
            Queries.SAVE_AGGREGATE(agg).execute
          }.provideEnvironment(ZEnvironment(pool))

        def readEvents[Event: BinaryCodec](id: Key, discriminator: String): Task[Chunk[Change[Event]]] =
          transaction {
            Queries.READ_EVENTS(id, discriminator).selectAll
          }.provideEnvironment(ZEnvironment(pool))

        def saveEvent[Event: BinaryCodec](id: Key, discriminator: String, event: Change[Event]): Task[Unit] =
          transaction {
            Queries.SAVE_EVENT(id, discriminator, event).execute
          }.provideEnvironment(ZEnvironment(pool))

        def readSnapshot(id: Key): Task[Option[Version]] =
          transaction {
            Queries.READ_SNAPSHOT(id).selectOne
          }.provideEnvironment(ZEnvironment(pool))

        def saveSnapshot(id: Key, version: Version): Task[Unit] =
          transaction {
            Queries.SAVE_SNAPSHOT(id, version).execute
          }.provideEnvironment(ZEnvironment(pool))
      }

    }

  object Queries {

    inline def READ_AGGREGATE(id: Key): Query[Aggregate] =
      sql"""
      SELECT
        version
      FROM aggregates
      WHERE id = $id
      """.query[Version].map(Aggregate(id = id, _))

    inline def SAVE_AGGREGATE(agg: Aggregate): SqlFragment =
      sql"""
      INSERT INTO aggregates (id, version)
      VALUES (${agg.id}, ${agg.version})
      """

    inline def READ_EVENTS[Event: BinaryCodec](id: Key, discriminator: String): Query[Change[Event]] =
      sql"""
      SELECT
        payload,
        timestamp
      FROM events
      WHERE aggregate_id = $id AND discriminator = $discriminator
      ORDER BY timestamp ASC
      """.query[(Event, Timestamp)].map(Change.apply)

    inline def SAVE_EVENT[Event: BinaryCodec](id: Key, discriminator: String, event: Change[Event]): SqlFragment =
      sql"""
      INSERT INTO events (aggregate_id, discriminator, payload, timestamp)
      VALUES ($id, $discriminator, ${summon[BinaryCodec.BinaryEncoder[Event]].encode(event.payload)}, ${event.timestamp.value})
      """

    inline def READ_SNAPSHOT(id: Key): Query[Version] =
      sql"""
      SELECT version
      FROM snapshots
      WHERE aggregate_id = $id
      """.query[Version]

    inline def SAVE_SNAPSHOT(id: Key, version: Version): SqlFragment =
      sql"""
      INSERT INTO snapshots (aggregate_id, version)
      VALUES ($id, $version)
      ON CONFLICT (aggregate_id) DO UPDATE SET version = $version
      """
  }

}

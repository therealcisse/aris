package com.github
package aris
package service
package postgres

import com.github.aris.domain.*

import zio.schema.codec.*

import zio.interop.catz.*

import doobie.*
import doobie.util.transactor.Transactor
import doobie.implicits.*

trait PostgresCQRSPersistence extends CQRSPersistence {}

object PostgresCQRSPersistence {
  import zio.*
  import zio.schema.*

  def live(xa: Transactor[Task]): ZLayer[Any, Nothing, CQRSPersistence] =
    ZLayer.succeed(new PostgresCQRSPersistenceLive(xa))

  class PostgresCQRSPersistenceLive(xa: Transactor[Task]) extends CQRSPersistence { self =>
    def readEvent[Event: {BinaryCodec, Tag, MetaInfo}](
      version: Version,
      catalog: Catalog,
      ): Task[Option[Change[Event]]] =
        Queries.READ_EVENT[Event](version, catalog).option.transact(xa)

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      id: Key,
      discriminator: Discriminator,
      catalog: Catalog,
    ): Task[Chunk[Change[Event]]] =
      Queries.READ_EVENTS(id, discriminator, catalog).to[Chunk].transact(xa)

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      id: Key,
      discriminator: Discriminator,
      snapshotVersion: Version,
      catalog: Catalog,
    ): Task[Chunk[Change[Event]]] =
      Queries.READ_EVENTS(id, discriminator, snapshotVersion, catalog).to[Chunk].transact(xa)

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      discriminator: Discriminator,
      query: PersistenceQuery,
      options: FetchOptions,
      catalog: Catalog,
    ): Task[Chunk[Change[Event]]] =
      Queries.READ_EVENTS(discriminator, query, options, catalog).to[Chunk].transact(xa)

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      id: Key,
      discriminator: Discriminator,
      query: PersistenceQuery,
      options: FetchOptions,
      catalog: Catalog,
    ): Task[Chunk[Change[Event]]] =
      Queries.READ_EVENTS(id, discriminator, query, options, catalog).to[Chunk].transact(xa)

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      discriminator: Discriminator,
      query: PersistenceQuery,
      interval: TimeInterval,
      catalog: Catalog,
      ): Task[Chunk[Change[Event]]] =
      Queries.READ_EVENTS(discriminator, query, interval, catalog).to[Chunk].transact(xa)

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      id: Key,
      discriminator: Discriminator,
      query: PersistenceQuery,
      interval: TimeInterval,
      catalog: Catalog,
      ): Task[Chunk[Change[Event]]] =
      Queries.READ_EVENTS(id, discriminator, query, interval, catalog).to[Chunk].transact(xa)

    def saveEvent[Event: {BinaryCodec, MetaInfo, Tag}](
      id: Key,
      discriminator: Discriminator,
      event: Change[Event],
      catalog: Catalog,
    ): Task[Int] =
      Queries.SAVE_EVENT(id, discriminator, event, catalog).run.transact(xa)

    def readSnapshot(id: Key): Task[Option[Version]] =
      Queries.READ_SNAPSHOT(id).option.transact(xa)

    def saveSnapshot(id: Key, version: Version): Task[Int] =
      Queries.SAVE_SNAPSHOT(id, version).run.transact(xa)

  }

  object Queries extends JdbcCodecs {

    def READ_EVENT[Event: BinaryCodec](version: Version, catalog: Catalog): Query0[Change[Event]] =
      given Read[Event] = byteArrayReader[Event]

      fr"""SELECT payload FROM ${Fragment.const(catalog.tableName)} WHERE version = $version ORDER BY version ASC""".query[Event].map {
        case event => Change(version, event)
      }

    def READ_EVENTS[Event: BinaryCodec](id: Key, discriminator: Discriminator, catalog: Catalog): Query0[Change[Event]] =
      given Read[Event] = byteArrayReader[Event]

      (fr"SELECT version, payload FROM ${Fragment.const(catalog.tableName)} WHERE aggregate_id = $id AND discriminator = $discriminator ORDER BY version ASC")
        .query[(Version, Event)]
        .map { case (version, event) => Change(version, event) }

    def READ_EVENTS[Event: BinaryCodec](
      id: Key,
      discriminator: Discriminator,
      snapshotVersion: Version,
      catalog: Catalog,
    ): Query0[Change[Event]] =
      given Read[Event] = byteArrayReader[Event]

      (
        fr"""
        SELECT version, payload
        FROM ${Fragment.const(catalog.tableName)}
        WHERE aggregate_id = $id AND discriminator = $discriminator AND version > $snapshotVersion
        ORDER BY version ASC
        """
      ).query[(Version, Event)].map(Change.apply)

    def READ_EVENTS[Event: BinaryCodec](
      discriminator: Discriminator,
      query: PersistenceQuery,
      options: FetchOptions,
      catalog: Catalog,
    ): Query0[Change[Event]] =
      given Read[Event] = byteArrayReader[Event]

      val (
        offsetQuery,
        limitQuery,
        orderQuery,
      ) = options.toSql

      (
        sql"""SELECT version, payload FROM ${Fragment.const(catalog.tableName)} WHERE discriminator = $discriminator""" ++ query.toSql.fold(Fragment.empty)(ql => sql" AND " ++ ql) ++ offsetQuery.fold(Fragment.empty)(ql => sql" AND " ++ ql) ++
        orderQuery ++ limitQuery.fold(Fragment.empty)(ql => sql" " ++ ql)
      ).query[
      (
        Version,
        Event,
        )
      ].map(Change.apply)

    def READ_EVENTS[Event: BinaryCodec](
      id: Key,
      discriminator: Discriminator,
      query: PersistenceQuery,
      options: FetchOptions,
      catalog: Catalog,
    ): Query0[Change[Event]] =
      given Read[Event] = byteArrayReader[Event]

      val (
        offsetQuery,
        limitQuery,
        orderQuery,
      ) = options.toSql

      (
        fr"""SELECT version, payload FROM ${Fragment.const(catalog.tableName)} WHERE aggregate_id = $id AND discriminator = $discriminator""" ++ query.toSql.fold(Fragment.empty)(ql => sql" AND " ++ ql) ++ offsetQuery.fold(Fragment.empty)(ql => sql" AND " ++ ql) ++
        orderQuery ++ limitQuery.fold(Fragment.empty)(ql => sql" " ++ ql)
      ).query[
      (
        Version,
        Event,
        )
      ].map(Change.apply)

    def READ_EVENTS[Event: BinaryCodec](
      id: Key,
      discriminator: Discriminator,
      query: PersistenceQuery,
      interval: TimeInterval,
      catalog: Catalog,
    ): Query0[Change[Event]] =
      given Read[Event] = byteArrayReader[Event]

      (
        fr"""SELECT version, payload FROM ${Fragment.const(catalog.tableName)} WHERE aggregate_id = $id AND discriminator = $discriminator AND """ ++ interval.toSql ++ query.toSql.fold(Fragment.empty)(ql => sql" AND " ++ ql) ++ sql" ORDER BY version ASC"
      ).query[
      (
        Version,
        Event,
        )
      ].map(Change.apply)

    def READ_EVENTS[Event: BinaryCodec](
      discriminator: Discriminator,
      query: PersistenceQuery,
      interval: TimeInterval,
      catalog: Catalog,
    ): Query0[Change[Event]] =
      given Read[Event] = byteArrayReader[Event]

      (
        fr"""SELECT version, payload FROM ${Fragment.const(catalog.tableName)} WHERE discriminator = $discriminator AND """ ++ interval.toSql ++ query.toSql.fold(Fragment.empty)(ql => sql" AND " ++ ql) ++ sql" ORDER BY version ASC"
      ).query[
      (
        Version,
        Event,
        )
      ].map(Change.apply)

    def SAVE_EVENT[Event: { BinaryCodec, MetaInfo }](
      id: Key,
      discriminator: Discriminator,
      event: Change[Event],
      catalog: Catalog,
    ): Update0 =
      val payload = java.util.Base64.getEncoder.encodeToString(summon[BinaryCodec[Event]].encode(event.payload).toArray)

      val q =
        fr"""
        INSERT INTO ${Fragment.const(catalog.tableName)}(
          'version',
          'aggregate_id',
          'discriminator',
          'namespace',
          'payload',
          'timestamp'
        )
        VALUES (
          ${event.version},
          ${id},
          ${discriminator},
          ${event.payload.namespace},
          decode(${payload}, 'base64'),
          ${event.payload.timestamp `getOrElse` event.version.timestamp}
        )
        """

      q.update


    def READ_SNAPSHOT(id: Key): Query0[Version] =
      sql"""
      SELECT version
      FROM snapshots
      WHERE aggregate_id = $id
      """.query[Version]

    def SAVE_SNAPSHOT(id: Key, version: Version): Update0 =
      sql"""
      INSERT INTO snapshots (aggregate_id, version)
      VALUES ($id, $version)
      ON CONFLICT (aggregate_id) DO UPDATE SET version = $version
      """.update
  }

}


package com.github
package aris
package service
package postgres

import com.github.aris.domain.*

import zio.schema.codec.*

import zio.interop.catz.*
import zio.prelude.*

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
      catalog: Catalog,
    ): Task[Chunk[Change[Event]]] =
        Queries.READ_EVENTS(id, catalog).to[Chunk].transact(xa)

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      id: Key,
      options: FetchOptions,
      catalog: Catalog,
    ): Task[Chunk[Change[Event]]] =
        Queries.READ_EVENTS(id, options, catalog).to[Chunk].transact(xa)

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      discriminator: Discriminator,
      namespace: Namespace,
      options: FetchOptions,
      catalog: Catalog,
    ): Task[Chunk[Change[Event]]] =
        Queries.READ_EVENTS(discriminator, namespace, options, catalog).to[Chunk].transact(xa)

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      namespace: Namespace,
      options: FetchOptions,
      catalog: Catalog,
      tag: EventTag,
    ): Task[Chunk[Change[Event]]] =
        Queries.READ_EVENTS(namespace, options, catalog, tag).to[Chunk].transact(xa)

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      discriminator: Discriminator,
      namespace: Namespace,
      interval: TimeInterval,
      catalog: Catalog,
      ): Task[Chunk[Change[Event]]] =
        Queries.READ_EVENTS(discriminator, namespace, interval, catalog).to[Chunk].transact(xa)

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      id: Key,
      interval: TimeInterval,
      catalog: Catalog,
      ): Task[Chunk[Change[Event]]] =
        Queries.READ_EVENTS(id, interval, catalog).to[Chunk].transact(xa)

      def saveEvent[Event: {BinaryCodec, MetaInfo, Tag}](
        id: Key,
        discriminator: Discriminator,
        namespace: Namespace,
        event: Change[Event],
        catalog: Catalog,
      ): Task[Int] =
        (for {
          _ <- Queries.SAVE_EVENT(id, discriminator, namespace, event, catalog).run
          _ <- NonEmptyList.fromIterableOption(summon[MetaInfo[Event]].tags(event.payload)) match {
            case None => FC.unit
            case Some(tags) => Queries.ADD_TAGS(event.version, tags).run
          }
        } yield ()).transact(xa) `as` 1

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

    def READ_EVENTS[Event: BinaryCodec](id: Key, catalog: Catalog): Query0[Change[Event]] =
      given Read[Event] = byteArrayReader[Event]

      (fr"SELECT e.version, e.payload FROM ${Fragment.const(catalog.tableName)} e" ++ fr" WHERE e.aggregate_id = $id" ++ fr" ORDER BY e.version ASC")
        .query[(Version, Event)]
        .map { case (version, event) => Change(version, event) }

    def READ_EVENTS[Event: BinaryCodec](
      id: Key,
      snapshotVersion: Version,
      catalog: Catalog,
    ): Query0[Change[Event]] =
      given Read[Event] = byteArrayReader[Event]

      (
        fr"""
        SELECT e.version, e.payload
        FROM ${Fragment.const(catalog.tableName)} e""" ++ fr" WHERE e.aggregate_id = $id AND e.version > $snapshotVersion" ++ fr" ORDER BY e.version ASC"
      ).query[(Version, Event)].map(Change.apply)

    def READ_EVENTS[Event: BinaryCodec](
      discriminator: Discriminator,
      namespace: Namespace,
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
        fr"""SELECT e.version, e.payload FROM ${Fragment.const(catalog.tableName)} e""" ++ fr""" WHERE e.discriminator = $discriminator AND """ ++ namespace.toSql ++ offsetQuery.fold(Fragment.empty)(ql => sql" AND " ++ ql) ++
        orderQuery ++ limitQuery.fold(Fragment.empty)(ql => sql" " ++ ql)
      ).query[
      (
        Version,
        Event,
        )
      ].map(Change.apply)

    def READ_EVENTS[Event: BinaryCodec](
      id: Key,
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
        fr"""SELECT e.version, e.payload FROM ${Fragment.const(catalog.tableName)} e""" ++ fr""" WHERE e.aggregate_id = $id AND """ ++ offsetQuery.fold(Fragment.empty)(ql => sql" AND " ++ ql) ++
        orderQuery ++ limitQuery.fold(Fragment.empty)(ql => sql" " ++ ql)
      ).query[
      (
        Version,
        Event,
        )
      ].map(Change.apply)

    def READ_EVENTS[Event: BinaryCodec](
      id: Key,
      interval: TimeInterval,
      catalog: Catalog,
    ): Query0[Change[Event]] =
      given Read[Event] = byteArrayReader[Event]

      (
        fr"""SELECT e.version, e.payload FROM ${Fragment.const(catalog.tableName)} e""" ++ fr""" WHERE e.aggregate_id = $id AND """ ++ interval.toSql ++ sql" AND " ++ sql" ORDER BY e.version ASC"
      ).query[
      (
        Version,
        Event,
        )
      ].map(Change.apply)

    def READ_EVENTS[Event: BinaryCodec](
      discriminator: Discriminator,
      namespace: Namespace,
      interval: TimeInterval,
      catalog: Catalog,
    ): Query0[Change[Event]] =
      given Read[Event] = byteArrayReader[Event]

      (
        fr"""SELECT e.version, e.payload FROM ${Fragment.const(catalog.tableName)} e""" ++ fr""" WHERE e.discriminator = $discriminator AND """ ++ interval.toSql ++ sql" AND " ++ namespace.toSql ++ sql" ORDER BY e.version ASC"
      ).query[
      (
        Version,
        Event,
        )
      ].map(Change.apply)

    def READ_EVENTS[Event: BinaryCodec](
      namespace: Namespace,
      options: FetchOptions,
      catalog: Catalog,
      tag: EventTag,
    ): Query0[Change[Event]] =
      given Read[Event] = byteArrayReader[Event]

      val (
        offsetQuery,
        limitQuery,
        orderQuery,
      ) = options.toSql

      (
        fr"""SELECT e.version, e.payload FROM ${Fragment.const(catalog.tableName)} e JOIN tags t ON e.version = t.version""" ++
          fr""" WHERE """ ++ namespace.toSql ++ fr" AND " ++ tag.toSql ++ offsetQuery.fold(Fragment.empty)(ql => sql" AND " ++ ql) ++
          orderQuery ++ limitQuery.fold(Fragment.empty)(ql => sql" " ++ ql)
      ).query[
      (
        Version,
        Event,
      )
      ].map(Change.apply)

    def SAVE_EVENT[Event: { BinaryCodec, MetaInfo }](
      id: Key,
      discriminator: Discriminator,
      namespace: Namespace,
      event: Change[Event],
      catalog: Catalog,
    ): Update0 =
      val payload = java.util.Base64.getEncoder.encodeToString(summon[BinaryCodec[Event]].encode(event.payload).toArray)

      val q =
        fr"""
        INSERT INTO ${Fragment.const(catalog.tableName)}(
          version,
          aggregate_id,
          discriminator,
          namespace,
          payload,
          timestamp
        )
        VALUES (
          ${event.version},
          ${id},
          ${discriminator},
          ${namespace},
          decode(${payload}, 'base64'),
          ${event.payload.timestamp `getOrElse` event.version.timestamp}
        )
        """

      q.update

    def ADD_TAGS(version: Version, tags: NonEmptyList[EventTag]): Update0 = {
      val insertValues = tags.toList.map(tag => s"($version, '$tag')").mkString(", ")
      val sqlString = s"INSERT INTO tags (version, tag) VALUES $insertValues"
      Update0(sqlString, None)
    }

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


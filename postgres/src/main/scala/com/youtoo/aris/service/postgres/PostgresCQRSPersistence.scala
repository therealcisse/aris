package com.youtoo
package aris
package service
package postgres

import com.youtoo.aris.domain.*

import zio.schema.codec.*

trait PostgresCQRSPersistence extends CQRSPersistence {}

object PostgresCQRSPersistence {
  import zio.*
  import zio.jdbc.*
  import zio.schema.*

  def live(): ZLayer[TransactionManager[ZConnection], Throwable, CQRSPersistence] =
    ZLayer.fromFunction((tx: TransactionManager[ZConnection]) => new PostgresCQRSPersistenceLive(tx))

  class PostgresCQRSPersistenceLive(tx: TransactionManager[ZConnection]) extends CQRSPersistence { self =>
    def readEvent[Event: {BinaryCodec, Tag, MetaInfo}](
      version: Version,
      catalog: Catalog,
      ): Task[Option[Change[Event]]] =
        tx(Queries.READ_EVENT(version, catalog).selectOne)

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      id: Key,
      discriminator: Discriminator,
      catalog: Catalog,
    ): Task[Chunk[Change[Event]]] =
      tx(Queries.READ_EVENTS(id, discriminator, catalog).selectAll)

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      id: Key,
      discriminator: Discriminator,
      snapshotVersion: Version,
      catalog: Catalog,
    ): Task[Chunk[Change[Event]]] =
      tx(Queries.READ_EVENTS(id, discriminator, snapshotVersion, catalog).selectAll)

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      discriminator: Discriminator,
      query: PersistenceQuery,
      options: FetchOptions,
      catalog: Catalog,
    ): Task[Chunk[Change[Event]]] =
      tx(Queries.READ_EVENTS(discriminator, query, options, catalog).selectAll)

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      id: Key,
      discriminator: Discriminator,
      query: PersistenceQuery,
      options: FetchOptions,
      catalog: Catalog,
    ): Task[Chunk[Change[Event]]] =
      tx(Queries.READ_EVENTS(id, discriminator, query, options, catalog).selectAll)

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      discriminator: Discriminator,
      query: PersistenceQuery,
      interval: TimeInterval,
      catalog: Catalog,
      ): Task[Chunk[Change[Event]]] =
      tx(Queries.READ_EVENTS(discriminator, query, interval, catalog).selectAll)

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      id: Key,
      discriminator: Discriminator,
      query: PersistenceQuery,
      interval: TimeInterval,
      catalog: Catalog,
      ): Task[Chunk[Change[Event]]] =
      tx(Queries.READ_EVENTS(id, discriminator, query, interval, catalog).selectAll)

    def saveEvent[Event: {BinaryCodec, MetaInfo, Tag}](
      id: Key,
      discriminator: Discriminator,
      event: Change[Event],
      catalog: Catalog,
    ): Task[Long] =
      tx(Queries.SAVE_EVENT(id, discriminator, event, catalog).insert)

    def readSnapshot(id: Key): Task[Option[Version]] =
      tx(Queries.READ_SNAPSHOT(id).selectOne)

    def saveSnapshot(id: Key, version: Version): Task[Long] =
      tx(Queries.SAVE_SNAPSHOT(id, version).insert)

  }

  object Queries extends JdbcCodecs {

    def READ_EVENT[Event: BinaryCodec](version: Version, catalog: Catalog): Query[Change[Event]] =
      given JdbcDecoder[Event] = byteArrayDecoder[Event]

      SqlFragment
        .select("version", "payload")
        .from(catalog.tableName)
        .where(
          sql"""
          version = $version
          ORDER BY version ASC
          """
        ).query[(Version, Event)].map(Change.apply)

    def READ_EVENTS[Event: BinaryCodec](id: Key, discriminator: Discriminator, catalog: Catalog): Query[Change[Event]] =
      given JdbcDecoder[Event] = byteArrayDecoder[Event]

      SqlFragment
        .select("version", "payload")
        .from(catalog.tableName)
        .where(
          sql"""
          aggregate_id = $id AND discriminator = $discriminator
          ORDER BY version ASC
          """
        ).query[(Version, Event)].map(Change.apply)

    def READ_EVENTS[Event: BinaryCodec](
      id: Key,
      discriminator: Discriminator,
      snapshotVersion: Version,
      catalog: Catalog,
    ): Query[Change[Event]] =
      given JdbcDecoder[Event] = byteArrayDecoder[Event]

      SqlFragment
        .select("version", "payload")
        .from(catalog.tableName)
        .where(
          sql"""
          aggregate_id = $id AND discriminator = $discriminator AND version > $snapshotVersion
          ORDER BY version ASC
          """
        ).query[(Version, Event)].map(Change.apply)

    def READ_EVENTS[Event: BinaryCodec](
      discriminator: Discriminator,
      query: PersistenceQuery,
      options: FetchOptions,
      catalog: Catalog,
    ): Query[Change[Event]] =
      given JdbcDecoder[Event] = byteArrayDecoder[Event]

      val (
        offsetQuery,
        limitQuery,
        orderQuery,
      ) = options.toSql

      SqlFragment
        .select("version", "payload")
        .from(catalog.tableName)
        .where(
          sql"""discriminator = $discriminator""" ++ query.toSql.fold(SqlFragment.empty)(ql => sql" AND " ++ ql) ++ offsetQuery.fold(SqlFragment.empty)(ql => sql" AND " ++ ql) ++
          orderQuery ++ limitQuery.fold(SqlFragment.empty)(ql => sql" " ++ ql)
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
    ): Query[Change[Event]] =
      given JdbcDecoder[Event] = byteArrayDecoder[Event]

      val (
        offsetQuery,
        limitQuery,
        orderQuery,
      ) = options.toSql

      SqlFragment
        .select("version", "payload")
        .from(catalog.tableName)
        .where(
          sql"""aggregate_id = $id AND discriminator = $discriminator""" ++ query.toSql.fold(SqlFragment.empty)(ql => sql" AND " ++ ql) ++ offsetQuery.fold(SqlFragment.empty)(ql => sql" AND " ++ ql) ++
          orderQuery ++ limitQuery.fold(SqlFragment.empty)(ql => sql" " ++ ql)
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
    ): Query[Change[Event]] =
      given JdbcDecoder[Event] = byteArrayDecoder[Event]

      SqlFragment
        .select("version", "payload")
        .from(catalog.tableName)
        .where(
          sql"""aggregate_id = $id AND discriminator = $discriminator AND """ ++ interval.toSql ++ query.toSql.fold(SqlFragment.empty)(ql => sql" AND " ++ ql) ++ sql" ORDER BY version ASC"
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
    ): Query[Change[Event]] =
      given JdbcDecoder[Event] = byteArrayDecoder[Event]

      SqlFragment
        .select("version", "payload")
        .from(catalog.tableName)
        .where(
          sql"""discriminator = $discriminator AND """ ++ interval.toSql ++ query.toSql.fold(SqlFragment.empty)(ql => sql" AND " ++ ql) ++ sql" ORDER BY version ASC"
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
    ): SqlFragment =
      val payload = java.util.Base64.getEncoder.encodeToString(summon[BinaryCodec[Event]].encode(event.payload).toArray)

      val props = toJson(event.payload.props).asString

      event.payload.hierarchy match {
        case None =>

          SqlFragment.insertInto(catalog.tableName)(
            "version",
            "aggregate_id",
            "discriminator",
            "namespace",
            "props",
            "payload",
            "timestamp",

            ) ++ sql"""
          VALUES (
            ${event.version},
            ${id},
            ${discriminator},
            ${event.payload.namespace},
            (
              SELECT COALESCE(
                jsonb_object_agg(each.key, each.value),
                '{}'::jsonb
              ) AS props
              FROM jsonb_array_elements($props::jsonb) AS elem,
              LATERAL jsonb_each(elem) each
            ),
            decode(${payload}, 'base64'),
            ${event.payload.timestamp getOrElse event.version.timestamp}
          )
          """

        case Some(Hierarchy.GrandChild(grandParentId)) =>
          SqlFragment.insertInto(catalog.tableName)(
            "version",
            "aggregate_id",
            "discriminator",
            "namespace",
            "grand_parent_id",
            "props",
            "payload",
            "timestamp",

            ) ++ sql"""
          VALUES (
            ${event.version},
            ${id},
            ${discriminator},
            ${event.payload.namespace},
            $grandParentId,
            (
              SELECT COALESCE(
                jsonb_object_agg(each.key, each.value),
                '{}'::jsonb
              ) AS props
              FROM jsonb_array_elements($props::jsonb) AS elem,
              LATERAL jsonb_each(elem) each
            ),
            decode(${payload}, 'base64'),
            ${event.payload.timestamp getOrElse event.version.timestamp}
          )
          """

        case Some(Hierarchy.Child(parentId)) =>
          SqlFragment.insertInto(catalog.tableName)(
            "version",
            "aggregate_id",
            "discriminator",
            "namespace",
            "parent_id",
            "props",
            "payload",
            "timestamp",

            ) ++ sql"""
          VALUES (
            ${event.version},
            ${id},
            ${discriminator},
            ${event.payload.namespace},
            $parentId,
            (
              SELECT COALESCE(
                jsonb_object_agg(each.key, each.value),
                '{}'::jsonb
              ) AS props
              FROM jsonb_array_elements($props::jsonb) AS elem,
              LATERAL jsonb_each(elem) each
            ),
            decode(${payload}, 'base64'),
            ${event.payload.timestamp getOrElse event.version.timestamp}
          )
          """

        case Some(Hierarchy.Descendant(grandParentId, parentId)) =>
          SqlFragment.insertInto(catalog.tableName)(
            "version",
            "aggregate_id",
            "discriminator",
            "namespace",
            "parent_id",
            "grand_parent_id",
            "props",
            "payload",
            "timestamp",

            ) ++ sql"""
          VALUES (
            ${event.version},
            ${id},
            ${discriminator},
            ${event.payload.namespace},
            $parentId,
            $grandParentId,
            (
              SELECT COALESCE(
                jsonb_object_agg(each.key, each.value),
                '{}'::jsonb
              ) AS props
              FROM jsonb_array_elements($props::jsonb) AS elem,
              LATERAL jsonb_each(elem) each
            ),

            decode(${payload}, 'base64'),
            ${event.payload.timestamp getOrElse event.version.timestamp}
          )
          """


      }

    def READ_SNAPSHOT(id: Key): Query[Version] =
      sql"""
      SELECT version
      FROM snapshots
      WHERE aggregate_id = $id
      """.query[Version]

    def SAVE_SNAPSHOT(id: Key, version: Version): SqlFragment =
      sql"""
      INSERT INTO snapshots (aggregate_id, version)
      VALUES ($id, $version)
      ON CONFLICT (aggregate_id) DO UPDATE SET version = $version
      """
  }

}


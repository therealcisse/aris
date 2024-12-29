package com.youtoo
package cqrs
package service
package postgres

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*

import com.youtoo.cqrs.domain.*

import zio.schema.codec.*

import java.nio.charset.StandardCharsets

trait PostgresCQRSPersistence extends CQRSPersistence {}

object PostgresCQRSPersistence {
  import zio.*
  import zio.jdbc.*
  import zio.schema.*

  def live(): ZLayer[Tracing, Throwable, CQRSPersistence] =
    ZLayer.fromFunction(new PostgresCQRSPersistenceLive().traced(_))

  class PostgresCQRSPersistenceLive() extends CQRSPersistence { self =>
    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      id: Key,
      discriminator: Discriminator,
      catalog: Catalog,
    ): RIO[ZConnection, Chunk[Change[Event]]] =
      Queries.READ_EVENTS(id, discriminator, catalog).selectAll

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      id: Key,
      discriminator: Discriminator,
      snapshotVersion: Version,
      catalog: Catalog,
    ): RIO[ZConnection, Chunk[Change[Event]]] =
      Queries.READ_EVENTS(id, discriminator, snapshotVersion, catalog).selectAll

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      discriminator: Discriminator,
      query: PersistenceQuery,
      options: FetchOptions,
      catalog: Catalog,
    ): RIO[ZConnection, Chunk[Change[Event]]] =
      Queries.READ_EVENTS(discriminator, query, options, catalog).selectAll

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      id: Key,
      discriminator: Discriminator,
      query: PersistenceQuery,
      options: FetchOptions,
      catalog: Catalog,
    ): RIO[ZConnection, Chunk[Change[Event]]] =
      Queries.READ_EVENTS(id, discriminator, query, options, catalog).selectAll

    def saveEvent[Event: {BinaryCodec, MetaInfo, Tag}](
      id: Key,
      discriminator: Discriminator,
      event: Change[Event],
      catalog: Catalog,
    ): RIO[ZConnection, Long] =
      Queries.SAVE_EVENT(id, discriminator, event, catalog).insert

    def readSnapshot(id: Key): RIO[ZConnection, Option[Version]] =
      Queries.READ_SNAPSHOT(id).selectOne

    def saveSnapshot(id: Key, version: Version): RIO[ZConnection, Long] =
      Queries.SAVE_SNAPSHOT(id, version).insert

    def traced(tracing: Tracing): CQRSPersistence =
      new CQRSPersistence {
        def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
          id: Key,
          discriminator: Discriminator,
          catalog: Catalog,
        ): RIO[ZConnection, Chunk[Change[Event]]] =
          self.readEvents(id, discriminator, catalog) @@ tracing.aspects.span(
            "PostgresCQRSPersistence.readEvents",
            attributes = Attributes(
              Attribute.long("key", id.value),
              Attribute.string("discriminator", discriminator.value),
            ),
          )

        def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
          id: Key,
          discriminator: Discriminator,
          snapshotVersion: Version,
          catalog: Catalog,
        ): RIO[ZConnection, Chunk[Change[Event]]] =
          self.readEvents(id, discriminator, snapshotVersion, catalog) @@ tracing.aspects.span(
            "PostgresCQRSPersistence.readEvents.fromSnapshot",
            attributes = Attributes(
              Attribute.long("key", id.value),
              Attribute.string("discriminator", discriminator.value),
            ),
          )

        def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
          discriminator: Discriminator,
          query: PersistenceQuery,
          options: FetchOptions,
          catalog: Catalog,
        ): RIO[ZConnection, Chunk[Change[Event]]] =
          self.readEvents(discriminator, query, options, catalog) @@ tracing.aspects.span(
            "PostgresCQRSPersistence.readEvents.query",
            attributes = Attributes(Attribute.string("discriminator", discriminator.value)),
          )

        def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
          id: Key,
          discriminator: Discriminator,
          query: PersistenceQuery,
          options: FetchOptions,
          catalog: Catalog,
        ): RIO[ZConnection, Chunk[Change[Event]]] =
          self.readEvents(id, discriminator, query, options, catalog) @@ tracing.aspects.span(
            "PostgresCQRSPersistence.readEventsAggregate.query",
            attributes = Attributes(
              Attribute.long("key", id.value),
              Attribute.string("discriminator", discriminator.value),
            ),
          )

        def saveEvent[Event: {BinaryCodec, MetaInfo, Tag}](
          id: Key,
          discriminator: Discriminator,
          event: Change[Event],
          catalog: Catalog,
        ): RIO[ZConnection, Long] =
          self.saveEvent(id, discriminator, event, catalog) @@ tracing.aspects.span(
            "PostgresCQRSPersistence.saveEvent",
            attributes = Attributes(
              Attribute.long("key", id.value),
              Attribute.string("discriminator", discriminator.value),
            ),
          )

        def readSnapshot(id: Key): RIO[ZConnection, Option[Version]] =
          self.readSnapshot(id) @@ tracing.aspects.span(
            "PostgresCQRSPersistence.readSnapshot",
            attributes = Attributes(Attribute.long("snapshotId", id.value)),
          )

        def saveSnapshot(id: Key, version: Version): RIO[ZConnection, Long] =
          self.saveSnapshot(id, version) @@ tracing.aspects.span(
            "PostgresCQRSPersistence.saveSnapshot",
            attributes = Attributes(Attribute.long("snapshotId", id.value)),
          )
      }

  }

  object Queries extends JdbcCodecs {

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

    def SAVE_EVENT[Event: { BinaryCodec, MetaInfo }](
      id: Key,
      discriminator: Discriminator,
      event: Change[Event],
      catalog: Catalog,
    ): SqlFragment =
      val payload = java.util.Base64.getEncoder.encodeToString(summon[BinaryCodec[Event]].encode(event.payload).toArray)

      val props = String(toJson(event.payload.props).toArray, StandardCharsets.UTF_8.name)

      event.payload.hierarchy match {
        case None =>

          SqlFragment.insertInto(catalog.tableName)(
            "version",
            "aggregate_id",
            "discriminator",
            "namespace",
            "props",
            "payload",

            ) ++ sql"""
          VALUES (
            ${event.version},
            ${id},
            ${discriminator},
            ${event.payload.namespace},
            (
              SELECT COALESCE(
                  jsonb_object_agg(elem->>'key', elem->>'value'),
                  '{}'::jsonb
              )
              FROM jsonb_array_elements($props :: jsonb) as elem
              ),
            decode(${payload}, 'base64')
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

            ) ++ sql"""
          VALUES (
            ${event.version},
            ${id},
            ${discriminator},
            ${event.payload.namespace},
            $grandParentId,
            (
              SELECT COALESCE(
                  jsonb_object_agg(elem->>'key', elem->>'value'),
                  '{}'::jsonb
              )
              FROM jsonb_array_elements($props :: jsonb) as elem

              ),
            decode(${payload}, 'base64')
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

            ) ++ sql"""
          VALUES (
            ${event.version},
            ${id},
            ${discriminator},
            ${event.payload.namespace},
            $parentId,
            (
              SELECT COALESCE(
                  jsonb_object_agg(elem->>'key', elem->>'value'),
                  '{}'::jsonb
              )
              FROM jsonb_array_elements($props :: jsonb) as elem

              ),
            decode(${payload}, 'base64')
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
                  jsonb_object_agg(elem->>'key', elem->>'value'),
                  '{}'::jsonb
              )
              FROM jsonb_array_elements($props :: jsonb) as elem

              ),
            decode(${payload}, 'base64')
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

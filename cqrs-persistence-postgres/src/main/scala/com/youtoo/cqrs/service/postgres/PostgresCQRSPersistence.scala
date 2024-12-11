package com.youtoo
package cqrs
package service
package postgres

import zio.telemetry.opentelemetry.tracing.Tracing

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
    ): RIO[ZConnection, Chunk[Change[Event]]] =
      Queries.READ_EVENTS(id, discriminator).selectAll

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      id: Key,
      discriminator: Discriminator,
      snapshotVersion: Version,
    ): RIO[ZConnection, Chunk[Change[Event]]] =
      Queries.READ_EVENTS(id, discriminator, snapshotVersion).selectAll

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      discriminator: Discriminator,
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Chunk[Change[Event]]] =
      Queries.READ_EVENTS(discriminator, query, options).selectAll

    def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
      discriminator: Discriminator,
      snapshotVersion: Version,
      query: PersistenceQuery,
      options: FetchOptions,
    ): RIO[ZConnection, Chunk[Change[Event]]] =
      Queries.READ_EVENTS(discriminator, snapshotVersion, query, options).selectAll

    def saveEvent[Event: {BinaryCodec, MetaInfo, Tag}](
      id: Key,
      discriminator: Discriminator,
      event: Change[Event],
    ): RIO[ZConnection, Long] =
      Queries.SAVE_EVENT(id, discriminator, event).insert

    def readSnapshot(id: Key): RIO[ZConnection, Option[Version]] =
      Queries.READ_SNAPSHOT(id).selectOne

    def saveSnapshot(id: Key, version: Version): RIO[ZConnection, Long] =
      Queries.SAVE_SNAPSHOT(id, version).insert

    def traced(tracing: Tracing): CQRSPersistence =
      new CQRSPersistence {
        def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
          id: Key,
          discriminator: Discriminator,
        ): RIO[ZConnection, Chunk[Change[Event]]] =
          self.readEvents(id, discriminator) @@ tracing.aspects.span("PostgresCQRSPersistence.readEvents")

        def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
          id: Key,
          discriminator: Discriminator,
          snapshotVersion: Version,
        ): RIO[ZConnection, Chunk[Change[Event]]] =
          self.readEvents(id, discriminator, snapshotVersion) @@ tracing.aspects.span(
            "PostgresCQRSPersistence.readEvents.fromSnapshot",
          )

        def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
          discriminator: Discriminator,
          query: PersistenceQuery,
          options: FetchOptions,
        ): RIO[ZConnection, Chunk[Change[Event]]] =
          self.readEvents(discriminator, query, options) @@ tracing.aspects.span(
            "PostgresCQRSPersistence.readEvents.query",
          )

        def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
          discriminator: Discriminator,
          snapshotVersion: Version,
          query: PersistenceQuery,
          options: FetchOptions,
        ): RIO[ZConnection, Chunk[Change[Event]]] =
          self.readEvents(discriminator, snapshotVersion, query, options) @@ tracing.aspects.span(
            "PostgresCQRSPersistence.readEvents.query_fromSnapshot",
          )

        def saveEvent[Event: {BinaryCodec, MetaInfo, Tag}](
          id: Key,
          discriminator: Discriminator,
          event: Change[Event],
        ): RIO[ZConnection, Long] =
          self.saveEvent(id, discriminator, event) @@ tracing.aspects.span("PostgresCQRSPersistence.saveEvent")

        def readSnapshot(id: Key): RIO[ZConnection, Option[Version]] =
          self.readSnapshot(id) @@ tracing.aspects.span("PostgresCQRSPersistence.readSnapshot")

        def saveSnapshot(id: Key, version: Version): RIO[ZConnection, Long] =
          self.saveSnapshot(id, version) @@ tracing.aspects.span("PostgresCQRSPersistence.saveSnapshot")
      }

  }

  object Queries extends JdbcCodecs {

    def READ_EVENTS[Event: BinaryCodec](id: Key, discriminator: Discriminator): Query[Change[Event]] =
      given JdbcDecoder[Event] = byteArrayDecoder[Event]

      sql"""
      SELECT
        version,
        payload
      FROM events
      WHERE aggregate_id = $id AND discriminator = $discriminator
      ORDER BY version ASC
      """.query[(Version, Event)].map(Change.apply)

    def READ_EVENTS[Event: BinaryCodec](
      id: Key,
      discriminator: Discriminator,
      snapshotVersion: Version,
    ): Query[Change[Event]] =
      given JdbcDecoder[Event] = byteArrayDecoder[Event]

      sql"""
      SELECT
        version,
        payload
      FROM events
      WHERE aggregate_id = $id AND discriminator = $discriminator AND version > $snapshotVersion
      ORDER BY version ASC
      """.query[(Version, Event)].map(Change.apply)

    def READ_EVENTS[Event: BinaryCodec](
      discriminator: Discriminator,
      query: PersistenceQuery,
      options: FetchOptions,
    ): Query[Change[Event]] =
      given JdbcDecoder[Event] = byteArrayDecoder[Event]

      (sql"""
      SELECT
        version,
        payload
      FROM events
      WHERE discriminator = $discriminator""" ++ query.toSql.fold(SqlFragment.empty)(sql => sql" AND " ++ sql) ++ options.toSql.fold(SqlFragment.empty)(sql => sql" AND " ++ sql) ++
        sql""" ORDER BY version ASC""").query[(Version, Event)].map(Change.apply)

    def READ_EVENTS[Event: BinaryCodec](
      discriminator: Discriminator,
      snapshotVersion: Version,
      query: PersistenceQuery,
      options: FetchOptions,
    ): Query[Change[Event]] =
      given JdbcDecoder[Event] = byteArrayDecoder[Event]

      (sql"""
      SELECT
        version,
        payload
      FROM events
      WHERE discriminator = $discriminator
        AND version > $snapshotVersion""" ++ query.toSql.fold(SqlFragment.empty)(sql => sql" AND " ++ sql) ++ options.toSql.fold(SqlFragment.empty)(sql => sql" AND " ++ sql) ++
        sql""" ORDER BY version ASC""").query[(Version, Event)].map(Change.apply)

    def SAVE_EVENT[Event: { BinaryCodec, MetaInfo }](
      id: Key,
      discriminator: Discriminator,
      event: Change[Event],
    ): SqlFragment =
      val payload = java.util.Base64.getEncoder.encodeToString(summon[BinaryCodec[Event]].encode(event.payload).toArray)

      val props = String(toJson(event.payload.props).toArray, StandardCharsets.UTF_8.name)

      event.payload.hierarchy match {
        case None =>
          sql"""
          INSERT INTO events (version, aggregate_id, discriminator, namespace, props, payload)
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
          sql"""
          INSERT INTO events (version, aggregate_id, discriminator, namespace, grand_parent_id, props, payload)
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
          sql"""
          INSERT INTO events (version, aggregate_id, discriminator, namespace, parent_id, props, payload)
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
          sql"""
          INSERT INTO events (version, aggregate_id, discriminator, namespace, parent_id, grand_parent_id, props, payload)
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

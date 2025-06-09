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
    def readEvent[Event: {BinaryCodec, EventTag, MetaInfo}](
      version: Version,
      catalog: Catalog,
      ): Task[Option[Change[Event]]] =
        Queries.READ_EVENT[Event](version, catalog).option.transact(xa)

  def readEvents[Event: {BinaryCodec, EventTag, MetaInfo}](
    id: Key,
    discriminator: Discriminator,
    tag: Option[EvenTag],
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]] =
      Queries.READ_EVENTS(id, discriminator, tag, catalog).to[Chunk].transact(xa)

  def readEvents[Event: {BinaryCodec, EventTag, MetaInfo}](
    id: Key,
    discriminator: Discriminator,
    snapshotVersion: Version,
    tag: Option[EvenTag],
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]] =
      Queries.READ_EVENTS(id, discriminator, snapshotVersion, tag, catalog).to[Chunk].transact(xa)

  def readEvents[Event: {BinaryCodec, EventTag, MetaInfo}](
    discriminator: Discriminator,
    namespace: Namespace,
    tag: Option[EvenTag],
    options: FetchOptions,
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]] =
      Queries.READ_EVENTS(discriminator, namespace, tag, options, catalog).to[Chunk].transact(xa)

  def readEvents[Event: {BinaryCodec, EventTag, MetaInfo}](
    id: Key,
    discriminator: Discriminator,
    namespace: Namespace,
    tag: Option[EvenTag],
    options: FetchOptions,
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]] =
      Queries.READ_EVENTS(id, discriminator, namespace, tag, options, catalog).to[Chunk].transact(xa)

  def readEvents[Event: {BinaryCodec, EventTag, MetaInfo}](
    discriminator: Discriminator,
    namespace: Namespace,
    tag: Option[EvenTag],
    interval: TimeInterval,
    catalog: Catalog,
    ): Task[Chunk[Change[Event]]] =
      Queries.READ_EVENTS(discriminator, namespace, tag, interval, catalog).to[Chunk].transact(xa)

  def readEvents[Event: {BinaryCodec, EventTag, MetaInfo}](
    id: Key,
    discriminator: Discriminator,
    namespace: Namespace,
    tag: Option[EvenTag],
    interval: TimeInterval,
    catalog: Catalog,
    ): Task[Chunk[Change[Event]]] =
      Queries.READ_EVENTS(id, discriminator, namespace, tag, interval, catalog).to[Chunk].transact(xa)

    def saveEvent[Event: {BinaryCodec, MetaInfo, EventTag}](
      id: Key,
      discriminator: Discriminator,
      event: Change[Event],
      catalog: Catalog,
    ): Task[Int] =
      for {
        _ <- Queries.SAVE_EVENT(id, discriminator, event, catalog).run.transact(xa)
        _ <- ZIO.foreachDiscard(summon[MetaInfo[Event]].tags(event.payload)) { t =>
               Queries.ADD_TAG(event.version, t).run.transact(xa)
             }
      } yield 1

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

    def READ_EVENTS[Event: BinaryCodec](id: Key, discriminator: Discriminator, tag: Option[EvenTag], catalog: Catalog): Query0[Change[Event]] =
      given Read[Event] = byteArrayReader[Event]

      val tagJoin = tag.fold(Fragment.empty)(_ => fr" JOIN tags t ON e.version = t.version")
      val tagFilter = tag.fold(Fragment.empty)(t => sql" AND " ++ t.toSql)

      (fr"SELECT e.version, e.payload FROM ${Fragment.const(catalog.tableName)} e" ++ tagJoin ++ fr" WHERE e.aggregate_id = $id AND e.discriminator = $discriminator" ++ tagFilter ++ fr" ORDER BY e.version ASC")
        .query[(Version, Event)]
        .map { case (version, event) => Change(version, event) }

    def READ_EVENTS[Event: BinaryCodec](
      id: Key,
      discriminator: Discriminator,
      snapshotVersion: Version,
      tag: Option[EvenTag],
      catalog: Catalog,
    ): Query0[Change[Event]] =
      given Read[Event] = byteArrayReader[Event]

      val tagJoin = tag.fold(Fragment.empty)(_ => fr" JOIN tags t ON e.version = t.version")
      val tagFilter = tag.fold(Fragment.empty)(t => sql" AND " ++ t.toSql)

      (
        fr"""
        SELECT e.version, e.payload
        FROM ${Fragment.const(catalog.tableName)} e""" ++ tagJoin ++ fr" WHERE e.aggregate_id = $id AND e.discriminator = $discriminator AND e.version > $snapshotVersion" ++ tagFilter ++ fr" ORDER BY e.version ASC"
      ).query[(Version, Event)].map(Change.apply)

    def READ_EVENTS[Event: BinaryCodec](
      discriminator: Discriminator,
      namespace: Namespace,
      tag: Option[EvenTag],
      options: FetchOptions,
      catalog: Catalog,
    ): Query0[Change[Event]] =
      given Read[Event] = byteArrayReader[Event]

      val (
        offsetQuery,
        limitQuery,
        orderQuery,
      ) = options.toSql

      val tagJoin = tag.fold(Fragment.empty)(_ => fr" JOIN tags t ON e.version = t.version")
      val tagFilter = tag.fold(Fragment.empty)(t => sql" AND " ++ t.toSql)

      (
        fr"""SELECT e.version, e.payload FROM ${Fragment.const(catalog.tableName)} e""" ++ tagJoin ++ fr" WHERE e.discriminator = $discriminator AND """ ++ namespace.toSql ++ tagFilter ++ offsetQuery.fold(Fragment.empty)(ql => sql" AND " ++ ql) ++
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
      namespace: Namespace,
      tag: Option[EvenTag],
      options: FetchOptions,
      catalog: Catalog,
    ): Query0[Change[Event]] =
      given Read[Event] = byteArrayReader[Event]

      val (
        offsetQuery,
        limitQuery,
        orderQuery,
      ) = options.toSql

      val tagJoin = tag.fold(Fragment.empty)(_ => fr" JOIN tags t ON e.version = t.version")
      val tagFilter = tag.fold(Fragment.empty)(t => sql" AND " ++ t.toSql)

      (
        fr"""SELECT e.version, e.payload FROM ${Fragment.const(catalog.tableName)} e""" ++ tagJoin ++ fr" WHERE e.aggregate_id = $id AND e.discriminator = $discriminator AND """ ++ namespace.toSql ++ tagFilter ++ offsetQuery.fold(Fragment.empty)(ql => sql" AND " ++ ql) ++
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
      namespace: Namespace,
      tag: Option[EvenTag],
      interval: TimeInterval,
      catalog: Catalog,
    ): Query0[Change[Event]] =
      given Read[Event] = byteArrayReader[Event]

      val tagJoin = tag.fold(Fragment.empty)(_ => fr" JOIN tags t ON e.version = t.version")
      val tagFilter = tag.fold(Fragment.empty)(t => sql" AND " ++ t.toSql)

      (
        fr"""SELECT e.version, e.payload FROM ${Fragment.const(catalog.tableName)} e""" ++ tagJoin ++ fr" WHERE e.aggregate_id = $id AND e.discriminator = $discriminator AND """ ++ interval.toSql ++ sql" AND " ++ namespace.toSql ++ tagFilter ++ sql" ORDER BY e.version ASC"
      ).query[
      (
        Version,
        Event,
        )
      ].map(Change.apply)

    def READ_EVENTS[Event: BinaryCodec](
      discriminator: Discriminator,
      namespace: Namespace,
      tag: Option[EvenTag],
      interval: TimeInterval,
      catalog: Catalog,
    ): Query0[Change[Event]] =
      given Read[Event] = byteArrayReader[Event]

      val tagJoin = tag.fold(Fragment.empty)(_ => fr" JOIN tags t ON e.version = t.version")
      val tagFilter = tag.fold(Fragment.empty)(t => sql" AND " ++ t.toSql)

      (
        fr"""SELECT e.version, e.payload FROM ${Fragment.const(catalog.tableName)} e""" ++ tagJoin ++ fr" WHERE e.discriminator = $discriminator AND """ ++ interval.toSql ++ sql" AND " ++ namespace.toSql ++ tagFilter ++ sql" ORDER BY e.version ASC"
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
          ${event.payload.namespace},
          decode(${payload}, 'base64'),
          ${event.payload.timestamp `getOrElse` event.version.timestamp}
        )
        """

      q.update

    def ADD_TAG(version: Version, tag: EventTag): Update0 =
      sql"INSERT INTO tags (version, tag) VALUES ($version, $tag)".update


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


package com.youtoo
package cqrs
package service

import zio.mock.*

import com.youtoo.cqrs.domain.*

import zio.*
import zio.jdbc.*
import zio.schema.codec.*

object MockCQRSPersistence extends Mock[CQRSPersistence] {
  object ReadEvent extends Poly.Effect.Output[(Version, Catalog), Throwable]
  object ReadEvents {
    object Full extends Poly.Effect.Output[(Key, Discriminator, Catalog), Throwable]
    object Snapshot extends Poly.Effect.Output[(Key, Discriminator, Version, Catalog), Throwable]

    object FullArgs
        extends Poly.Effect.Output[
          (Discriminator, PersistenceQuery, FetchOptions, Catalog),
          Throwable,
        ]
    object FullArgsByAggregate
        extends Poly.Effect.Output[
          (
            Key,
            Discriminator,
            PersistenceQuery,
            FetchOptions,
            Catalog
          ),
          Throwable,
        ]
    object SnapshotArgs
        extends Poly.Effect.Output[
          (
            Discriminator,
            Version,
            PersistenceQuery,
            FetchOptions,
            Catalog,
          ),
          Throwable,
        ]
  }

  object SaveEvent extends Poly.Effect.InputOutput[Throwable]

  object ReadSnapshot extends Effect[Key, Throwable, Option[Version]]
  object SaveSnapshot extends Effect[(Key, Version), Throwable, Long]

  val compose: URLayer[Proxy, CQRSPersistence] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new CQRSPersistence {
        def readEvent[Event: {BinaryCodec, Tag, MetaInfo}](
          version: Version,
          catalog: Catalog,
          ): RIO[ZConnection, Option[Change[Event]]] =
            proxy(
            ReadEvent.of[Option[Change[Event]]],
            (
              version,
              catalog,
            )
          )

        def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
          id: Key,
          discriminator: Discriminator,
          snapshotVersion: Version,
          catalog: Catalog,
        ): ZIO[ZConnection, Throwable, Chunk[Change[Event]]] =
            proxy(
            ReadEvents.Snapshot.of[Chunk[Change[Event]]],
            (
              id,
              discriminator,
              snapshotVersion,
              catalog,
            )
          )

        def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
          id: Key,
          discriminator: Discriminator,
          catalog: Catalog,
        ): ZIO[ZConnection, Throwable, Chunk[Change[Event]]] =
          proxy(
            ReadEvents.Full.of[Chunk[Change[Event]]],
            (
              id,
              discriminator,
              catalog,
            )
          )

        def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
          discriminator: Discriminator,
          query: PersistenceQuery,
          options: FetchOptions,
          catalog: Catalog,
        ): ZIO[ZConnection, Throwable, Chunk[Change[Event]]] =
          proxy(
            ReadEvents.FullArgs.of[Chunk[Change[Event]]],
            (
              discriminator,
              query,
              options,
              catalog,
            )
          )

        def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
          id: Key,
          discriminator: Discriminator,
          query: PersistenceQuery,
          options: FetchOptions,
          catalog: Catalog,
        ): ZIO[ZConnection, Throwable, Chunk[Change[Event]]] =
          proxy(
            ReadEvents.FullArgsByAggregate.of[Chunk[Change[Event]]],
            (
              id,
              discriminator,
              query,
              options,
              catalog,
            )
          )

        def saveEvent[Event: {BinaryCodec, MetaInfo, Tag}](
          id: Key,
          discriminator: Discriminator,
          event: Change[Event],
          catalog: Catalog,
        ): ZIO[ZConnection, Throwable, Long] =
          proxy(
            SaveEvent.of[
              (
                  Key,
                  Discriminator,
                  Change[Event],
                  Catalog,
              ),
              Long
            ],
            (
              id,
              discriminator,
              event,
              catalog,
            )
          )

        def readSnapshot(id: Key): ZIO[ZConnection, Throwable, Option[Version]] =
          proxy(ReadSnapshot, id)

        def saveSnapshot(id: Key, version: Version): ZIO[ZConnection, Throwable, Long] =
          proxy(SaveSnapshot, (id, version))
      }
    }
}

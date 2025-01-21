package com.youtoo
package aris

import zio.mock.*

import com.youtoo.aris.domain.*
import com.youtoo.aris.service.*

import zio.*
import zio.schema.codec.*

object MockCQRSPersistence extends Mock[CQRSPersistence] {
  object ReadEvent extends Poly.Effect.Output[(Version, Catalog), Throwable]
  object ReadEvents {
    object Full extends Poly.Effect.Output[(Key, Discriminator, Catalog), Throwable]
    object FullInterval extends Poly.Effect.Output[
      (
        Discriminator,
        PersistenceQuery,
        TimeInterval,
        Catalog,
      ),
      Throwable
    ]
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
    object FullArgsByAggregateInterval
        extends Poly.Effect.Output[
          (
            Key,
            Discriminator,
            PersistenceQuery,
            TimeInterval,
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
          ): RIO[Any, Option[Change[Event]]] =
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
        ): ZIO[Any, Throwable, Chunk[Change[Event]]] =
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
        ): ZIO[Any, Throwable, Chunk[Change[Event]]] =
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
          interval: TimeInterval,
          catalog: Catalog,
          ): Task[Chunk[Change[Event]]] =
          proxy(
            ReadEvents.FullInterval.of[Chunk[Change[Event]]],
            (
              discriminator,
              query,
              interval,
              catalog,
            )
          )

        def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
          discriminator: Discriminator,
          query: PersistenceQuery,
          options: FetchOptions,
          catalog: Catalog,
        ): ZIO[Any, Throwable, Chunk[Change[Event]]] =
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
        ): ZIO[Any, Throwable, Chunk[Change[Event]]] =
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

        def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
          id: Key,
          discriminator: Discriminator,
          query: PersistenceQuery,
          interval: TimeInterval,
          catalog: Catalog,
        ): ZIO[Any, Throwable, Chunk[Change[Event]]] =
          proxy(
            ReadEvents.FullArgsByAggregateInterval.of[Chunk[Change[Event]]],
            (
              id,
              discriminator,
              query,
              interval,
              catalog,
            )
          )

        def saveEvent[Event: {BinaryCodec, MetaInfo, Tag}](
          id: Key,
          discriminator: Discriminator,
          event: Change[Event],
          catalog: Catalog,
        ): ZIO[Any, Throwable, Long] =
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

        def readSnapshot(id: Key): ZIO[Any, Throwable, Option[Version]] =
          proxy(ReadSnapshot, id)

        def saveSnapshot(id: Key, version: Version): ZIO[Any, Throwable, Long] =
          proxy(SaveSnapshot, (id, version))
      }
    }
}

package com.youtoo.cqrs
package service

import zio.mock.*

import com.youtoo.cqrs.domain.*

import zio.*
import zio.jdbc.*
import zio.schema.codec.*

object MockCQRSPersistence extends Mock[CQRSPersistence] {
  case class ReadEvents[T: izumi.reflect.Tag]() extends Effect[(Key, Discriminator), Throwable, Chunk[Change[T]]]
  case class SaveEvent[T: izumi.reflect.Tag]() extends Effect[(Key, Discriminator, Change[T]), Throwable, Long]
  object ReadSnapshot extends Effect[Key, Throwable, Option[Version]]
  object SaveSnapshot extends Effect[(Key, Version), Throwable, Long]

  val compose: URLayer[Proxy, CQRSPersistence] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new CQRSPersistence {
        def atomically[T](fa: ZIO[ZConnection, Throwable, T]): ZIO[ZConnectionPool, Throwable, T] =
          ???

        def readEvents[Event: BinaryCodec](
          id: Key,
          discriminator: Discriminator,
          snapshotVersion: Version,
        ): ZIO[ZConnection, Throwable, Chunk[Change[Event]]] =
          ???

        def readEvents[Event: BinaryCodec](
          id: Key,
          discriminator: Discriminator,
        ): ZIO[ZConnection, Throwable, Chunk[Change[Event]]] =
          ???
          // proxy(ReadEvents[Event](), id, discriminator)

        def saveEvent[Event: BinaryCodec](
          id: Key,
          discriminator: Discriminator,
          event: Change[Event],
        ): ZIO[ZConnection, Throwable, Long] =
          ???
          // proxy(SaveEvent[Event](), id, discriminator, event)

        def readSnapshot(id: Key): ZIO[ZConnection, Throwable, Option[Version]] =
          proxy(ReadSnapshot, id)

        def saveSnapshot(id: Key, version: Version): ZIO[ZConnection, Throwable, Long] =
          proxy(SaveSnapshot, id, version)
      }
    }
}

package com.youtoo.cqrs
package service

import com.youtoo.cqrs.domain.*

import zio.*
import zio.jdbc.*
import zio.schema.codec.*

trait CQRSPersistence {
  def readEvents[Event: BinaryCodec](id: Key, discriminator: Discriminator): RIO[ZConnection, Chunk[Change[Event]]]
  def readEvents[Event: BinaryCodec](
    id: Key,
    discriminator: Discriminator,
    snapshotVersion: Version,
  ): RIO[ZConnection, Chunk[Change[Event]]]
  def saveEvent[Event: BinaryCodec](id: Key, discriminator: Discriminator, event: Change[Event]): RIO[ZConnection, Long]

  def readSnapshot(id: Key): RIO[ZConnection, Option[Version]]
  def saveSnapshot(id: Key, version: Version): RIO[ZConnection, Long]

  def atomically[T](fa: ZIO[ZConnection, Throwable, T]): ZIO[ZConnectionPool, Throwable, T]
}

object CQRSPersistence {
  inline def atomically[T](fa: ZIO[ZConnection, Throwable, T]): RIO[CQRSPersistence & ZConnectionPool, T] =
    ZIO.serviceWithZIO[CQRSPersistence](_.atomically(fa))

}

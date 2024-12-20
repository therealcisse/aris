package com.youtoo
package cqrs
package service

import com.youtoo.cqrs.domain.*

import zio.*
import zio.jdbc.*
import zio.schema.codec.*

trait CQRSPersistence {
  def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
    id: Key,
    discriminator: Discriminator,
    catalog: Catalog,
    ): RIO[ZConnection, Chunk[Change[Event]]]
  def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
    id: Key,
    discriminator: Discriminator,
    snapshotVersion: Version,
    catalog: Catalog,
    ): RIO[ZConnection, Chunk[Change[Event]]]
  def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
    discriminator: Discriminator,
    query: PersistenceQuery,
    options: FetchOptions,
    catalog: Catalog,
    ): RIO[ZConnection, Chunk[Change[Event]]]
  def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
    id: Key,
    discriminator: Discriminator,
    query: PersistenceQuery,
    options: FetchOptions,
    catalog: Catalog,
    ): RIO[ZConnection, Chunk[Change[Event]]]

  def saveEvent[Event: {BinaryCodec, MetaInfo, Tag}](
    id: Key,
    discriminator: Discriminator,
    event: Change[Event],
    catalog: Catalog,
    ): RIO[ZConnection, Long]

  def readSnapshot(id: Key): RIO[ZConnection, Option[Version]]
  def saveSnapshot(id: Key, version: Version): RIO[ZConnection, Long]

}

object CQRSPersistence {}

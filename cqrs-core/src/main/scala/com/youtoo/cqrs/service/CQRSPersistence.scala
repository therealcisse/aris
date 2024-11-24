package com.youtoo
package cqrs
package service

import com.youtoo.cqrs.domain.*

import zio.*
import zio.jdbc.*
import zio.prelude.*
import zio.schema.codec.*

trait CQRSPersistence {
  def readEvents[Event: BinaryCodec: Tag: MetaInfo](
    id: Key,
    discriminator: Discriminator,
  ): RIO[ZConnection, Chunk[Change[Event]]]
  def readEvents[Event: BinaryCodec: Tag: MetaInfo](
    id: Key,
    discriminator: Discriminator,
    snapshotVersion: Version,
  ): RIO[ZConnection, Chunk[Change[Event]]]
  def readEvents[Event: BinaryCodec: Tag: MetaInfo](
    discriminator: Discriminator,
    ns: Option[NonEmptyList[Namespace]],
    hierarchy: Option[Hierarchy],
    props: Option[NonEmptyList[EventProperty]],
  ): RIO[ZConnection, Chunk[Change[Event]]]
  def readEvents[Event: BinaryCodec: Tag: MetaInfo](
    discriminator: Discriminator,
    snapshotVersion: Version,
    ns: Option[NonEmptyList[Namespace]],
    hierarchy: Option[Hierarchy],
    props: Option[NonEmptyList[EventProperty]],
  ): RIO[ZConnection, Chunk[Change[Event]]]

  def saveEvent[Event: BinaryCodec: MetaInfo: Tag](
    id: Key,
    discriminator: Discriminator,
    event: Change[Event],
  ): RIO[ZConnection, Long]

  def readSnapshot(id: Key): RIO[ZConnection, Option[Version]]
  def saveSnapshot(id: Key, version: Version): RIO[ZConnection, Long]

}

object CQRSPersistence {}

package com.github
package aris
package service

import com.github.aris.domain.*

import zio.*
import zio.schema.codec.*

trait CQRSPersistence {
  def readEvent[Event: {BinaryCodec, Tag, MetaInfo}](
    version: Version,
    catalog: Catalog,
    ): Task[Option[Change[Event]]]
  def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
    id: Key,
    tag: Option[EventTag],
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]]
  def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
    id: Key,
    snapshotVersion: Version,
    tag: Option[EventTag],
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]]
  def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
    discriminator: Discriminator,
    namespace: Namespace,
    tag: Option[EventTag],
    options: FetchOptions,
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]]
  def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
    id: Key,
    tag: Option[EventTag],
    options: FetchOptions,
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]]

  def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
    discriminator: Discriminator,
    namespace: Namespace,
    tag: Option[EventTag],
    interval: TimeInterval,
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]]
  def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
    id: Key,
    tag: Option[EventTag],
    interval: TimeInterval,
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]]


  def saveEvent[Event: {BinaryCodec, MetaInfo, Tag}](
    id: Key,
    discriminator: Discriminator,
    namespace: Namespace,
    event: Change[Event],
    catalog: Catalog,
  ): Task[Int]

  def readSnapshot(id: Key): Task[Option[Version]]
  def saveSnapshot(id: Key, version: Version): Task[Int]

}

object CQRSPersistence {}

package com.youtoo.cqrs
package service

import com.youtoo.cqrs.domain.*

import zio.*
import zio.schema.codec.*

trait CQRSPersistence {
  def readAggregate(id: Key): Task[Option[Aggregate]]
  def saveAggregate(agg: Aggregate): Task[Unit]

  def readEvents[Event: BinaryCodec](id: Key, discriminator: String): Task[Chunk[Change[Event]]]
  def saveEvent[Event: BinaryCodec](id: Key, discriminator: String, event: Change[Event]): Task[Unit]

  def readSnapshot(id: Key): Task[Option[Version]]
  def saveSnapshot(id: Key, version: Version): Task[Unit]

}

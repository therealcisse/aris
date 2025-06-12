package com.github
package aris
package service

import com.github.aris.domain.*

import zio.*
import zio.schema.codec.*

/**
 * Trait for handling persistence of events and snapshots using CQRS (Command Query Responsibility Segregation).
 */
trait CQRSPersistence {
  /**
   * Reads a single event of a given version from the catalog.
   *
   * @tparam Event The type of the event.
   * @param version The version of the event to read.
   * @param catalog The catalog from where the event should be read.
   * @return An optional Change of the event if found.
   */
  def readEvent[Event: {BinaryCodec, Tag, MetaInfo}](
    version: Version,
    catalog: Catalog,
    ): Task[Option[Change[Event]]]

  /**
   * Reads all events associated with a given key from the catalog.
   *
   * @tparam Event The type of the events.
   * @param id The key identifying the events.
   * @param catalog The catalog from where the events should be read.
   * @return A chunk of changes containing the events.
   */
  def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
    id: Key,
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]]

  /**
   * Reads events associated with a given key and fetch options from the catalog.
   *
   * @tparam Event The type of the events.
   * @param id The key identifying the events.
   * @param options The options to customize the event fetching.
   * @param catalog The catalog from where the events should be read.
   * @return A chunk of changes containing the events.
   */
  def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
    id: Key,
    options: FetchOptions,
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]]

  /**
   * Reads events using discriminator, namespace, and fetch options from the catalog.
   *
   * @tparam Event The type of the events.
   * @param discriminator The discriminator to filter events.
   * @param namespace The namespace to filter events.
   * @param options The options to customize the event fetching.
   * @param catalog The catalog from where the events should be read.
   * @return A chunk of changes containing the events.
   */
  def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
    discriminator: Discriminator,
    namespace: Namespace,
    options: FetchOptions,
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]]

  /**
   * Reads events using discriminator, namespace, and time interval from the catalog.
   *
   * @tparam Event The type of the events.
   * @param discriminator The discriminator to filter events.
   * @param namespace The namespace to filter events.
   * @param interval The time interval to filter events.
   * @param catalog The catalog from where the events should be read.
   * @return A chunk of changes containing the events.
   */
  def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
    discriminator: Discriminator,
    namespace: Namespace,
    interval: TimeInterval,
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]]

  /**
   * Reads events associated with a given key and time interval from the catalog.
   *
   * @tparam Event The type of the events.
   * @param id The key identifying the events.
   * @param interval The time interval to filter events.
   * @param catalog The catalog from where the events should be read.
   * @return A chunk of changes containing the events.
   */
  def readEvents[Event: {BinaryCodec, Tag, MetaInfo}](
    id: Key,
    interval: TimeInterval,
    catalog: Catalog,
  ): Task[Chunk[Change[Event]]]

  /**
   * Saves an event to the catalog.
   *
   * @tparam Event The type of the event.
   * @param id The key associated with the event.
   * @param discriminator The discriminator for the event.
   * @param namespace The namespace for the event.
   * @param event The event to be saved.
   * @param catalog The catalog where the event should be saved.
   * @return The number of events saved.
   */
  def saveEvent[Event: {BinaryCodec, MetaInfo, Tag}](
    id: Key,
    discriminator: Discriminator,
    namespace: Namespace,
    event: Change[Event],
    catalog: Catalog,
  ): Task[Int]

  /**
   * Reads the snapshot version associated with a given key.
   *
   * @param id The key identifying the snapshot.
   * @return An optional version if found.
   */
  def readSnapshot(id: Key): Task[Option[Version]]

  /**
   * Saves the snapshot version associated with a given key.
   *
   * @param id The key identifying the snapshot.
   * @param version The version to be saved.
   * @return The number of snapshots saved.
   */
  def saveSnapshot(id: Key, version: Version): Task[Int]

}

object CQRSPersistence {}

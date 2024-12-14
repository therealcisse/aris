package com.youtoo
package migration
package store

import com.youtoo.cqrs.Codecs.given

import zio.test.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*
import zio.mock.*
import zio.jdbc.*
import zio.prelude.*
import com.youtoo.postgres.*
import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.service.MockCQRSPersistence
import com.youtoo.migration.model.*

object MigrationEventStoreSpec extends MockSpecDefault {

  def spec = suite("MigrationEventStore")(
    testReadEventsId,
    testReadEventsByIdAndVersion,
    testReadEventsQueryOptions,
    testSaveEvent,
  ).provideSomeLayerShared(
    ZConnectionMock.pool(),
  )

  val discriminator = MigrationEvent.discriminator

  val testReadEventsId = test("readEvents by Id") {
    check(keyGen, eventSequenceGen) { (id, events) =>
      val mockEnv = MockCQRSPersistence.ReadEvents.Full.of(
        equalTo((id, discriminator)),
        value(events.toChunk),
      )

      val effect = for {
        store <- ZIO.service[MigrationEventStore]
        result <- store.readEvents(id).atomically
      } yield assert(result)(equalTo(Option(events)))

      effect.provideSomeLayer[ZConnectionPool](mockEnv.toLayer >>> MigrationEventStore.live())
    }
  }

  val testReadEventsByIdAndVersion = test("readEvents by Id and Version") {
    check(keyGen, versionGen, eventSequenceGen) { (id, version, events) =>
      val mockEnv = MockCQRSPersistence.ReadEvents.Snapshot.of(
        equalTo((id, discriminator, version)),
        value(events.toChunk),
      )

      val effect = for {
        store <- ZIO.service[MigrationEventStore]
        result <- store.readEvents(id, version).atomically
      } yield assert(result)(equalTo(Option(events)))

      effect.provideSomeLayer[ZConnectionPool](mockEnv.toLayer >>> MigrationEventStore.live())
    }
  }

  val testReadEventsQueryOptions = test("readEvents by Query and Options") {
    check(eventSequenceGen) { events =>
      val query = PersistenceQuery.condition()
      val options = FetchOptions()

      val mockEnv = MockCQRSPersistence.ReadEvents.FullArgs.of(
        equalTo((discriminator, query, options)),
        value(events.toChunk),
      )

      val effect = for {
        store <- ZIO.service[MigrationEventStore]
        result <- store.readEvents(query, options).atomically
      } yield assert(result)(equalTo(Option(events)))

      effect.provideSomeLayer[ZConnectionPool](mockEnv.toLayer >>> MigrationEventStore.live())
    }
  }

  val testSaveEvent = test("save an event") {
    check(keyGen, versionGen, migrationEventGen) { (id, version, event) =>
      val change = Change(version, event)
      val returnId = 1L

      val mockEnv = MockCQRSPersistence.SaveEvent.of[(Key, Discriminator, Change[MigrationEvent]), Long](
        equalTo((id, discriminator, change)),
        value(returnId),
      )

      val effect = for {
        store <- ZIO.service[MigrationEventStore]
        result <- store.save(id, change).atomically
      } yield assert(result)(equalTo(returnId))

      effect.provideSomeLayer[ZConnectionPool](mockEnv.toLayer >>> MigrationEventStore.live())
    }
  }

}

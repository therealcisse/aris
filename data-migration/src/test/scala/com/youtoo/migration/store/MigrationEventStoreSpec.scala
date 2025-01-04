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

import zio.telemetry.opentelemetry.tracing.*

object MigrationEventStoreSpec extends MockSpecDefault, TestSupport {

  def spec = suite("MigrationEventStoreSpec")(
    testReadEventsId,
    testReadEventsByIdAndVersion,
    testReadEventsQueryOptions,
    testReadEventsQueryOptionsAggregate,
    testSaveEvent,
  ).provideSomeLayerShared(
    ZLayer.make[Tracing & ZConnectionPool](
      ZConnectionMock.pool(),
      tracingMockLayer(),
      zio.telemetry.opentelemetry.OpenTelemetry.contextZIO,
    ),
  )

  val discriminator = MigrationEvent.discriminator

  val testReadEventsId = test("readEvents by Id") {
    check(keyGen, eventSequenceGen) { (id, events) =>
      val mockEnv = MockCQRSPersistence.ReadEvents.Full.of(
        equalTo((id, discriminator, MigrationEventStore.Table)),
        value(events.toChunk),
      )

      val effect = for {
        store <- ZIO.service[MigrationEventStore]
        result <- store.readEvents(id).atomically
      } yield assert(result)(equalTo(Option(events)))

      effect.provideSomeLayer[ZConnectionPool & Tracing](mockEnv.toLayer >>> MigrationEventStore.live())
    }
  }

  val testReadEventsByIdAndVersion = test("readEvents by Id and Version") {
    check(keyGen, versionGen, eventSequenceGen) { (id, version, events) =>
      val mockEnv = MockCQRSPersistence.ReadEvents.Snapshot.of(
        equalTo((id, discriminator, version, MigrationEventStore.Table)),
        value(events.toChunk),
      )

      val effect = for {
        store <- ZIO.service[MigrationEventStore]
        result <- store.readEvents(id, version).atomically
      } yield assert(result)(equalTo(Option(events)))

      effect.provideSomeLayer[ZConnectionPool & Tracing](mockEnv.toLayer >>> MigrationEventStore.live())
    }
  }

  val testReadEventsQueryOptions = test("readEvents by Query and Options") {
    check(eventSequenceGen) { events =>
      val query = PersistenceQuery.condition()
      val options = FetchOptions()

      val mockEnv = MockCQRSPersistence.ReadEvents.FullArgs.of(
        equalTo((discriminator, query, options, MigrationEventStore.Table)),
        value(events.toChunk),
      )

      val effect = for {
        store <- ZIO.service[MigrationEventStore]
        result <- store.readEvents(query, options).atomically
      } yield assert(result)(equalTo(Option(events)))

      effect.provideSomeLayer[ZConnectionPool & Tracing](mockEnv.toLayer >>> MigrationEventStore.live())
    }
  }

  val testReadEventsQueryOptionsAggregate = test("readEvents by Query and Options aggregate") {
    check(keyGen, eventSequenceGen) { (id, events) =>
      val query = PersistenceQuery.condition()
      val options = FetchOptions()

      val mockEnv = MockCQRSPersistence.ReadEvents.FullArgsByAggregate.of(
        equalTo((id, discriminator, query, options, MigrationEventStore.Table)),
        value(events.toChunk),
      )

      val effect = for {
        store <- ZIO.service[MigrationEventStore]
        result <- store.readEvents(id, query, options).atomically
      } yield assert(result)(equalTo(Option(events)))

      effect.provideSomeLayer[ZConnectionPool & Tracing](mockEnv.toLayer >>> MigrationEventStore.live())
    }
  }

  val testSaveEvent = test("save an event") {
    check(keyGen, versionGen, migrationEventGen) { (id, version, event) =>
      val change = Change(version, event)
      val returnId = 1L

      val mockEnv = MockCQRSPersistence.SaveEvent.of[(Key, Discriminator, Change[MigrationEvent], Catalog), Long](
        equalTo((id, discriminator, change, MigrationEventStore.Table)),
        value(returnId),
      )

      val effect = for {
        store <- ZIO.service[MigrationEventStore]
        result <- store.save(id, change).atomically
      } yield assert(result)(equalTo(returnId))

      effect.provideSomeLayer[ZConnectionPool & Tracing](mockEnv.toLayer >>> MigrationEventStore.live())
    }
  }

}

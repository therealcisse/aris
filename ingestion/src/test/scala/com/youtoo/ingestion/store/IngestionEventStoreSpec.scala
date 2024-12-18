package com.youtoo
package ingestion
package store

import cats.implicits.*

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
import com.youtoo.ingestion.model.*

object IngestionEventStoreSpec extends MockSpecDefault {

  def spec = suite("IngestionEventStoreSpec")(
    testReadEventsId,
    testReadEventsTagVersion,
    testReadEventsQueryOptions,
    testSaveEvent,
  ).provideSomeLayerShared(
    ZConnectionMock.pool(),
  )

  val discriminator = IngestionEvent.discriminator

  val testReadEventsId = test("readEvents by Id") {
    check(keyGen, validIngestionEventSequenceGen) { (id, events) =>
      val mockEnv = MockCQRSPersistence.ReadEvents.Full.of(
        equalTo((id, discriminator, IngestionEventStore.Table)),
        value(events.toChunk),
      )

      val effect = for {
        store <- ZIO.service[IngestionEventStore]
        result <- store.readEvents(id).atomically
      } yield assert(result)(equalTo(events.some))

      effect.provideSomeLayer[ZConnectionPool](mockEnv.toLayer >>> IngestionEventStore.live())
    }
  }

  val testReadEventsTagVersion = test("readEvents by Id and Version") {
    check(keyGen, versionGen, validIngestionEventSequenceGen) { (id, version, events) =>
      val mockEnv = MockCQRSPersistence.ReadEvents.Snapshot.of[Chunk[Change[IngestionEvent]]](
        equalTo((id, discriminator, version, IngestionEventStore.Table)),
        value(events.toChunk),
      )

      val effect = for {
        store <- ZIO.service[IngestionEventStore]
        result <- store.readEvents(id, version).atomically
      } yield assert(result)(equalTo(events.some))

      effect.provideSomeLayer[ZConnectionPool](mockEnv.toLayer >>> IngestionEventStore.live())
    }
  }

  val testReadEventsQueryOptions = test("readEvents by Query and Options") {
    check(validIngestionEventSequenceGen) { events =>

      val query = PersistenceQuery.condition()
      val options = FetchOptions()

      val mockEnv = MockCQRSPersistence.ReadEvents.FullArgs.of(
        equalTo((discriminator, query, options, IngestionEventStore.Table)),
        value(events.toChunk),
      )

      val effect = for {
        store <- ZIO.service[IngestionEventStore]
        result <- store.readEvents(query, options).atomically
      } yield assert(result)(equalTo(events.some))

      effect.provideSomeLayer[ZConnectionPool](mockEnv.toLayer >>> IngestionEventStore.live())
    }
  }

  val testSaveEvent = test("save an event") {
    check(keyGen, versionGen, ingestionEventGen) { (id, version, event) =>

      val change = Change(version, event)
      val returnId = 1L

      val mockEnv = MockCQRSPersistence.SaveEvent.of[(Key, Discriminator, Change[IngestionEvent], Catalog), Long](
        equalTo((id, discriminator, change, IngestionEventStore.Table)),
        value(returnId),
      )

      val effect = for {
        store <- ZIO.service[IngestionEventStore]
        result <- store.save(id, change).atomically
      } yield assert(result)(equalTo(returnId))

      effect.provideSomeLayer[ZConnectionPool](mockEnv.toLayer >>> IngestionEventStore.live())
    }
  }
}

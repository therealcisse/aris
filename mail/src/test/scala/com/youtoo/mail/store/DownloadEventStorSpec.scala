package com.youtoo
package mail
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
import com.youtoo.mail.model.*

import zio.telemetry.opentelemetry.tracing.*

object DownloadEventStoreSpec extends MockSpecDefault, TestSupport {

  def spec = suite("DownloadEventStoreSpec")(
    testReadEventsId,
    testReadEventsByIdAndVersion,
    testReadEventsQueryOptions,
    testSaveEvent,
  ).provideSomeLayerShared(
    ZLayer.make[Tracing & ZConnectionPool](
      ZConnectionMock.pool(),
      tracingMockLayer(),
      zio.telemetry.opentelemetry.OpenTelemetry.contextZIO,
    ),
  )

  val discriminator = DownloadEvent.discriminator

  val testReadEventsId = test("readEvents by Id") {
    check(keyGen, Gen.listOf(downloadedChangeGen)) { (id, events) =>
      val mockEnv = MockCQRSPersistence.ReadEvents.Full.of(
        equalTo((id, discriminator, DownloadEventStore.Table)),
        value(events.toChunk),
      )

      val effect = for {
        store <- ZIO.service[DownloadEventStore]
        result <- store.readEvents(id).atomically
      } yield assert(result)(equalTo(NonEmptyList.fromIterableOption(events)))

      effect.provideSomeLayer[ZConnectionPool & Tracing](mockEnv.toLayer >>> DownloadEventStore.live())
    }
  }

  val testReadEventsByIdAndVersion = test("readEvents by Id and Version") {
    check(keyGen, versionGen, Gen.listOf(downloadedChangeGen)) { (id, version, events) =>
      val mockEnv = MockCQRSPersistence.ReadEvents.Snapshot.of(
        equalTo((id, discriminator, version, DownloadEventStore.Table)),
        value(events.toChunk),
      )

      val effect = for {
        store <- ZIO.service[DownloadEventStore]
        result <- store.readEvents(id, version).atomically
      } yield assert(result)(equalTo(NonEmptyList.fromIterableOption(events)))

      effect.provideSomeLayer[ZConnectionPool & Tracing](mockEnv.toLayer >>> DownloadEventStore.live())
    }
  }

  val testReadEventsQueryOptions = test("readEvents by Query and Options") {
    check(Gen.listOf(downloadedChangeGen)) { events =>
      val query = PersistenceQuery.condition()
      val options = FetchOptions()

      val mockEnv = MockCQRSPersistence.ReadEvents.FullArgs.of(
        equalTo((discriminator, query, options, DownloadEventStore.Table)),
        value(events.toChunk),
      )

      val effect = for {
        store <- ZIO.service[DownloadEventStore]
        result <- store.readEvents(query, options).atomically
      } yield assert(result)(equalTo(NonEmptyList.fromIterableOption(events)))

      effect.provideSomeLayer[ZConnectionPool & Tracing](mockEnv.toLayer >>> DownloadEventStore.live())
    }
  }

  val testReadEventsQueryOptionsAggregate = test("readEvents by Query and Options aggregate") {
    check(keyGen, Gen.listOf(downloadedChangeGen)) { (id, events) =>

      val query = PersistenceQuery.condition()
      val options = FetchOptions()

      val mockEnv = MockCQRSPersistence.ReadEvents.FullArgsByAggregate.of(
        equalTo((id, discriminator, query, options, DownloadEventStore.Table)),
        value(events.toChunk),
      )

      val effect = for {
        store <- ZIO.service[DownloadEventStore]
        result <- store.readEvents(id, query, options).atomically
      } yield assert(result)(equalTo(events.some))

      effect.provideSomeLayer[ZConnectionPool & Tracing](mockEnv.toLayer >>> DownloadEventStore.live())
    }
  }

  val testSaveEvent = test("save an event") {
    check(keyGen, versionGen, downloadedEventGen) { (id, version, event) =>
      val change = Change(version, event)
      val returnId = 1L

      val mockEnv = MockCQRSPersistence.SaveEvent.of[(Key, Discriminator, Change[DownloadEvent], Catalog), Long](
        equalTo((id, discriminator, change, DownloadEventStore.Table)),
        value(returnId),
      )

      val effect = for {
        store <- ZIO.service[DownloadEventStore]
        result <- store.save(id, change).atomically
      } yield assert(result)(equalTo(returnId))

      effect.provideSomeLayer[ZConnectionPool & Tracing](mockEnv.toLayer >>> DownloadEventStore.live())
    }
  }

}

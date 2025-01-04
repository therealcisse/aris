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

object MailEventStoreSpec extends MockSpecDefault, TestSupport {

  def spec = suite("MailEventStoreSpec")(
    testReadEventsId,
    testReadEventsTagVersion,
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

  val discriminator = MailEvent.discriminator

  val testReadEventsId = test("readEvents by Id") {
    check(keyGen, validMailEventSequenceGen()) { (id, events) =>
      val mockEnv = MockCQRSPersistence.ReadEvents.Full.of(
        equalTo((id, discriminator, MailEventStore.Table)),
        value(events.toChunk),
      )

      val effect = for {
        store <- ZIO.service[MailEventStore]
        result <- store.readEvents(id).atomically
      } yield assert(result)(equalTo(events.some))

      effect.provideSomeLayer[ZConnectionPool & Tracing](mockEnv.toLayer >>> MailEventStore.live())
    }
  }

  val testReadEventsTagVersion = test("readEvents by Id and Version") {
    check(keyGen, versionGen, validMailEventSequenceGen()) { (id, version, events) =>
      val mockEnv = MockCQRSPersistence.ReadEvents.Snapshot.of[Chunk[Change[MailEvent]]](
        equalTo((id, discriminator, version, MailEventStore.Table)),
        value(events.toChunk),
      )

      val effect = for {
        store <- ZIO.service[MailEventStore]
        result <- store.readEvents(id, version).atomically
      } yield assert(result)(equalTo(events.some))

      effect.provideSomeLayer[ZConnectionPool & Tracing](mockEnv.toLayer >>> MailEventStore.live())
    }
  }

  val testReadEventsQueryOptions = test("readEvents by Query and Options") {
    check(validMailEventSequenceGen()) { events =>

      val query = PersistenceQuery.condition()
      val options = FetchOptions()

      val mockEnv = MockCQRSPersistence.ReadEvents.FullArgs.of(
        equalTo((discriminator, query, options, MailEventStore.Table)),
        value(events.toChunk),
      )

      val effect = for {
        store <- ZIO.service[MailEventStore]
        result <- store.readEvents(query, options).atomically
      } yield assert(result)(equalTo(events.some))

      effect.provideSomeLayer[ZConnectionPool & Tracing](mockEnv.toLayer >>> MailEventStore.live())
    }
  }

  val testReadEventsQueryOptionsAggregate = test("readEvents by Query and Options aggregate") {
    check(keyGen, validMailEventSequenceGen()) { (id, events) =>

      val query = PersistenceQuery.condition()
      val options = FetchOptions()

      val mockEnv = MockCQRSPersistence.ReadEvents.FullArgsByAggregate.of(
        equalTo((id, discriminator, query, options, MailEventStore.Table)),
        value(events.toChunk),
      )

      val effect = for {
        store <- ZIO.service[MailEventStore]
        result <- store.readEvents(id, query, options).atomically
      } yield assert(result)(equalTo(events.some))

      effect.provideSomeLayer[ZConnectionPool & Tracing](mockEnv.toLayer >>> MailEventStore.live())
    }
  }

  val testSaveEvent = test("save an event") {
    check(keyGen, versionGen, mailEventGen) { (id, version, event) =>

      val change = Change(version, event)
      val returnId = 1L

      val mockEnv = MockCQRSPersistence.SaveEvent.of[(Key, Discriminator, Change[MailEvent], Catalog), Long](
        equalTo((id, discriminator, change, MailEventStore.Table)),
        value(returnId),
      )

      val effect = for {
        store <- ZIO.service[MailEventStore]
        result <- store.save(id, change).atomically
      } yield assert(result)(equalTo(returnId))

      effect.provideSomeLayer[ZConnectionPool & Tracing](mockEnv.toLayer >>> MailEventStore.live())
    }
  }
}

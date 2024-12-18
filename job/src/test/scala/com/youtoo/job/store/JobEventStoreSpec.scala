package com.youtoo
package job
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
import com.youtoo.job.model.*

object JobEventStoreSpec extends MockSpecDefault {

  def spec = suite("JobEventStore")(
    testReadEventsId,
    testReadEventsByIdAndVersion,
    testReadEventsQueryOptions,
    testSaveEvent,
  ).provideSomeLayerShared(
    ZConnectionMock.pool(),
  )

  val discriminator = JobEvent.discriminator

  val testReadEventsId = test("readEvents by Id") {
    check(keyGen, eventSequenceGen) { (id, events) =>
      val mockEnv = MockCQRSPersistence.ReadEvents.Full.of(
        equalTo((id, discriminator, JobEventStore.Table)),
        value(events.toChunk),
      )

      val effect = for {
        store <- ZIO.service[JobEventStore]
        result <- store.readEvents(id).atomically
      } yield assert(result)(equalTo(Option(events)))

      effect.provideSomeLayer[ZConnectionPool](mockEnv.toLayer >>> JobEventStore.live())
    }
  }

  val testReadEventsByIdAndVersion = test("readEvents by Id and Version") {
    check(keyGen, versionGen, eventSequenceGen) { (id, version, events) =>
      val mockEnv = MockCQRSPersistence.ReadEvents.Snapshot.of(
        equalTo((id, discriminator, version, JobEventStore.Table)),
        value(events.toChunk),
      )

      val effect = for {
        store <- ZIO.service[JobEventStore]
        result <- store.readEvents(id, version).atomically
      } yield assert(result)(equalTo(Option(events)))

      effect.provideSomeLayer[ZConnectionPool](mockEnv.toLayer >>> JobEventStore.live())
    }
  }

  val testReadEventsQueryOptions = test("readEvents by Query and Options") {
    check(eventSequenceGen) { events =>
      val query = PersistenceQuery.condition()
      val options = FetchOptions()

      val mockEnv = MockCQRSPersistence.ReadEvents.FullArgs.of(
        equalTo((discriminator, query, options, JobEventStore.Table)),
        value(events.toChunk),
      )

      val effect = for {
        store <- ZIO.service[JobEventStore]
        result <- store.readEvents(query, options).atomically
      } yield assert(result)(equalTo(Option(events)))

      effect.provideSomeLayer[ZConnectionPool](mockEnv.toLayer >>> JobEventStore.live())
    }
  }

  val testSaveEvent = test("save an event") {
    check(keyGen, versionGen, jobEventGen) { (id, version, event) =>
      val change = Change(version, event)
      val returnId = 1L

      val mockEnv = MockCQRSPersistence.SaveEvent.of[(Key, Discriminator, Change[JobEvent], Catalog), Long](
        equalTo((id, discriminator, change, JobEventStore.Table)),
        value(returnId),
      )

      val effect = for {
        store <- ZIO.service[JobEventStore]
        result <- store.save(id, change).atomically
      } yield assert(result)(equalTo(returnId))

      effect.provideSomeLayer[ZConnectionPool](mockEnv.toLayer >>> JobEventStore.live())
    }
  }

}

package com.youtoo
package ingestion
package service

import cats.implicits.*

import zio.test.*
import zio.prelude.*
import zio.mock.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*
import zio.jdbc.*

import com.youtoo.cqrs.service.*
import com.youtoo.cqrs.service.postgres.*

import com.youtoo.ingestion.repository.*

import com.youtoo.ingestion.model.*
import com.youtoo.cqrs.*
import com.youtoo.ingestion.store.*
import com.youtoo.cqrs.store.*

object IngestionServiceSpec extends MockSpecDefault {
  def spec = suite("IngestionServiceSpec")(
    test("should load ingestion") {
      check(
        keyGen,
        Gen.option(ingestionGen),
        Gen.option(versionGen),
        validEventSequenceGen,
        Gen.int(0, 12),
      ) { case (id, ingestion, version, events, amount) =>
        val chs = NonEmptyList(events.head, events.tail.take(amount)*)
        val sendSnapshot = chs.size >= 10
        val maxChange = chs.maxBy(_.version)

        val (key, deps) = (ingestion, version).tupled match {
          case None =>
            val in = summon[IngestionEventHandler].applyEvents(chs)

            val ingestionRepositoryLoadMock = IngestionRepositoryMock.Load(
              equalTo(in.id),
              value(None),
            )

            val ingestionServiceEnv =
              if sendSnapshot then
                IngestionServiceMock
                  .Save(
                    equalTo(in),
                    value(1L),
                  )
                  .toLayer
              else IngestionServiceMock.empty

            val snapshotStoreReadEnv = MockSnapshotStore.ReadSnapshot(
              equalTo(id),
              value(None),
            )

            val snapshotStoreSaveEnv =
              if sendSnapshot then
                MockSnapshotStore
                  .SaveSnapshot(
                    equalTo((id, maxChange.version)),
                    value(1L),
                  )
                  .toLayer
              else MockSnapshotStore.empty

            val providerEnv = IngestionServiceMock.Load(
              equalTo(id),
              value(None),
            )

            val eventStoreReadEnv = MockIngestionEventStore.ReadEvents.Full(
              equalTo(id),
              value(chs.some),
            )

            val ingestionServiceSaveMock =
              if sendSnapshot then
                IngestionServiceMock
                  .Save(
                    equalTo(ingestion),
                    value(1L),
                  )
                  .toLayer
              else IngestionServiceMock.empty

            (
              id,
              ingestionRepositoryLoadMock.toLayer ++ ingestionServiceEnv ++ snapshotStoreReadEnv.toLayer ++ snapshotStoreSaveEnv ++ providerEnv.toLayer ++ ingestionServiceSaveMock ++ eventStoreReadEnv.toLayer,
            )

          case Some((in @ Ingestion(id, _, _), v)) =>
            val ingestionServiceEnv =
              if sendSnapshot then
                IngestionServiceMock
                  .Save(
                    equalTo(in),
                    value(1L),
                  )
                  .toLayer
              else IngestionServiceMock.empty

            val ingestionRepositoryLoadMock = IngestionRepositoryMock.Load(
              equalTo(id),
              value(in.some),
            )

            val snapshotStoreReadEnv = MockSnapshotStore.ReadSnapshot(
              equalTo(id.asKey),
              value(v.some),
            )

            val snapshotStoreSaveEnv =
              if sendSnapshot then
                MockSnapshotStore
                  .SaveSnapshot(
                    equalTo((id.asKey, maxChange.version)),
                    value(1L),
                  )
                  .toLayer
              else MockSnapshotStore.empty

            val providerEnv = IngestionServiceMock.Load(
              equalTo(id.asKey),
              value(in.some),
            )

            val eventStoreReadEnv = MockIngestionEventStore.ReadEvents.Snapshot(
              equalTo((id.asKey, v)),
              value(chs.some),
            )

            val ingestionServiceSaveMock =
              if sendSnapshot then
                IngestionServiceMock
                  .Save(
                    equalTo(ingestion),
                    value(1L),
                  )
                  .toLayer
              else IngestionServiceMock.empty

            (
              id.asKey,
              ingestionRepositoryLoadMock.toLayer ++ ingestionServiceEnv ++ snapshotStoreReadEnv.toLayer ++ snapshotStoreSaveEnv ++ providerEnv.toLayer ++ ingestionServiceSaveMock ++ eventStoreReadEnv.toLayer,
            )

        }

        (for {

          _ <- IngestionService.load(Ingestion.Id(key))

        } yield assertCompletes)
          .provide((deps >+> ZConnectionMock.pool() ++ SnapshotStrategy.live()))
      }

    },
    test("load returns expected ingestion using IngestionRepository") {
      check(ingestionGen) { case expectedIngestion =>
        val ingestionId = expectedIngestion.id

        val mockSnapshot = MockSnapshotStore.ReadSnapshot(
          equalTo(ingestionId.asKey),
          value(Version(expectedIngestion.id.asKey.value).some),
        )

        val mockEnv = IngestionRepositoryMock.Load(
          equalTo(ingestionId),
          value(expectedIngestion.some),
        )

        val env = (mockEnv ++ mockSnapshot).toLayer

        (for {
          effect <- atomically(IngestionService.load(ingestionId))
          testResult = assert(effect)(equalTo(expectedIngestion.some))
        } yield testResult).provideSomeLayer[ZConnectionPool](
          env >>> ZLayer.makeSome[IngestionRepository & ZConnectionPool, IngestionService](
            PostgresCQRSPersistence.live(),
            IngestionEventStore.live(),
            SnapshotStore.live(),
            SnapshotStrategy.live(),
            IngestionService.live(),
          ),
        )

      }
    } @@ TestAspect.samples(1),
    test("save returns expected result using IngestionRepository") {
      check(ingestionGen) { case ingestion =>
        val expected = 1L

        val mockEnv = IngestionRepositoryMock.Save(
          equalTo(ingestion),
          value(expected),
        )

        (for {
          effect <- atomically(IngestionService.save(ingestion))
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool](
          mockEnv.toLayer >>> ZLayer.makeSome[IngestionRepository & ZConnectionPool, IngestionService](
            PostgresCQRSPersistence.live(),
            IngestionEventStore.live(),
            SnapshotStore.live(),
            SnapshotStrategy.live(),
            IngestionService.live(),
          ),
        )

      }
    } @@ TestAspect.samples(1),
    test("load many returns expected result using IngestionRepository") {
      check(Gen.option(keyGen), Gen.long, keyGen) { case (key, limit, id) =>
        val expected = Chunk(id)

        val mockEnv = IngestionRepositoryMock.LoadMany(
          equalTo((key, limit)),
          value(expected),
        )

        (for {
          effect <- atomically(IngestionService.loadMany(offset = key, limit = limit))
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool](
          mockEnv.toLayer >>> ZLayer.makeSome[IngestionRepository & ZConnectionPool, IngestionService](
            PostgresCQRSPersistence.live(),
            IngestionEventStore.live(),
            SnapshotStore.live(),
            SnapshotStrategy.live(),
            IngestionService.live(),
          ),
        )

      }
    } @@ TestAspect.samples(1),
  ).provideSomeLayerShared(ZConnectionMock.pool())

}

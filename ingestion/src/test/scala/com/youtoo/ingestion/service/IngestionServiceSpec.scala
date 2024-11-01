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
  inline val Threshold = 10

  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    testEnvironment ++ Runtime.setConfigProvider(
      ConfigProvider.fromMap(Map("Ingestion.snapshots.threshold" -> s"$Threshold")),
    )

  def spec = suite("IngestionServiceSpec")(
    test("should load ingestion") {
      check(
        Gen.option(ingestionGen),
        Gen.option(versionGen),
        validEventSequenceGen,
      ) { case (ingestion, version, events) =>
        val maxChange = events.maxBy(_.version)

        val (id, deps) = (ingestion, version).tupled match {
          case None =>
            val sendSnapshot = events.size >= Threshold
            val in @ Ingestion(id, _, _) = summon[EventHandler[IngestionEvent, Ingestion]].applyEvents(events)

            val loadMock = IngestionRepositoryMock.Load(equalTo(id), value(None))
            val readSnapshotMock = MockSnapshotStore.ReadSnapshot(equalTo(id), value(None))
            val saveMock = IngestionRepositoryMock.Save(equalTo(in), value(1L))
            val saveSnapshotMock = MockSnapshotStore.SaveSnapshot(equalTo((id.asKey, maxChange.version)), value(1L))
            val readEventsMock = MockIngestionEventStore.ReadEvents.Full(equalTo(id.asKey), value(events.some))

            val layers =
              if sendSnapshot then
                ((loadMock ++ readSnapshotMock) || (readSnapshotMock ++ loadMock)) ++ readEventsMock ++ ((saveMock ++ saveSnapshotMock) || (saveSnapshotMock ++ saveMock))
              else ((loadMock ++ readSnapshotMock) || (readSnapshotMock ++ loadMock)) ++ readEventsMock

            (id, layers.toLayer)

          case Some((in @ Ingestion(id, _, _), v)) =>
            val sendSnapshot = (events.size - 1) >= Threshold

            val es = NonEmptyList.fromIterableOption(events.tail)

            val inn = es match {
              case None => in
              case Some(nel) => summon[EventHandler[IngestionEvent, Ingestion]].applyEvents(in, nel)
            }

            val readSnapshotMock = MockSnapshotStore.ReadSnapshot(equalTo(id.asKey), value(v.some))
            val loadMock = IngestionRepositoryMock.Load(equalTo(id), value(in.some))
            val saveMock = IngestionRepositoryMock.Save(equalTo(inn), value(1L))
            val saveSnapshotMock = MockSnapshotStore.SaveSnapshot(equalTo((id.asKey, maxChange.version)), value(1L))
            val readEventsMock = MockIngestionEventStore.ReadEvents.Snapshot(equalTo((id.asKey, v)), value(es))

            val layers =
              if sendSnapshot then
                ((readSnapshotMock ++ loadMock) || (loadMock ++ readSnapshotMock)) ++ readEventsMock ++ ((saveMock ++ saveSnapshotMock) || (saveSnapshotMock ++ saveMock))
              else ((readSnapshotMock ++ loadMock) || (loadMock ++ readSnapshotMock)) ++ readEventsMock

            (id, layers.toLayer)
        }

        (for {

          _ <- IngestionService.load(id)

        } yield assertCompletes)
          .provideSomeLayer[ZConnectionPool]((deps ++ SnapshotStrategy.live()) >>> IngestionService.live())
      }

    },
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
  ).provideSomeLayerShared(ZConnectionMock.pool()) @@ TestAspect.withLiveClock

}

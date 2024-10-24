package com.youtoo
package ingestion

import cats.implicits.*

import zio.prelude.*
import zio.test.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*
import zio.jdbc.*

import com.youtoo.cqrs.*
import com.youtoo.ingestion.model.*
import com.youtoo.ingestion.service.*
import com.youtoo.ingestion.store.*

import com.youtoo.cqrs.service.*
import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.domain.*

import com.youtoo.cqrs.service.postgres.*

object IngestionCQRSSpec extends ZIOSpecDefault {

  def spec = suite("IngestionCQRSSpec")(
    test("should add command") {
      check(keyGen, ingestionCommandGen) { case (id, cmd) =>
        val eventStoreEnv = MockIngestionEventStore.Save(
          equalTo((id, anything)),
          value(1L),
        )

        (for {

          _ <- IngestionCQRS.add(id, cmd)

        } yield assertCompletes).provide(
          (
            eventStoreEnv.toLayer ++ ZConnectionMock
              .pool() ++ IngestionCheckpointerMock.empty ++ IngestionProviderMock.empty ++ MockSnapshotStore.empty ++ SnapshotStrategy.live()
          ) >>> IngestionCQRS.live(),
        )
      }

    },
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

            val providerEnv = IngestionProviderMock.Load(
              equalTo(id),
              value(None),
            )

            val eventStoreReadEnv = MockIngestionEventStore.ReadEvents.Full(
              equalTo(id),
              value(chs.some),
            )

            val checkpointEnv =
              if sendSnapshot then
                IngestionCheckpointerMock
                  .Save(
                    equalTo(ingestion),
                    unit,
                  )
                  .toLayer
              else IngestionCheckpointerMock.empty

            (
              id,
              ingestionServiceEnv ++ snapshotStoreReadEnv.toLayer ++ snapshotStoreSaveEnv ++ providerEnv.toLayer ++ checkpointEnv ++ eventStoreReadEnv.toLayer,
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

            val providerEnv = IngestionProviderMock.Load(
              equalTo(id.asKey),
              value(in.some),
            )

            val eventStoreReadEnv = MockIngestionEventStore.ReadEvents.Snapshot(
              equalTo((id.asKey, v)),
              value(chs.some),
            )

            val checkpointEnv =
              if sendSnapshot then
                IngestionCheckpointerMock
                  .Save(
                    equalTo(ingestion),
                    unit,
                  )
                  .toLayer
              else IngestionCheckpointerMock.empty

            (
              id.asKey,
              ingestionServiceEnv ++ snapshotStoreReadEnv.toLayer ++ snapshotStoreSaveEnv ++ providerEnv.toLayer ++ checkpointEnv ++ eventStoreReadEnv.toLayer,
            )

        }

        (for {

          _ <- IngestionCQRS.load(key)

        } yield assertCompletes).provide((deps ++ ZConnectionMock.pool() ++ SnapshotStrategy.live()) >>> IngestionCQRS.live())
      }

    },
  ) @@ TestAspect.withLiveClock @@ TestAspect.ignore
}

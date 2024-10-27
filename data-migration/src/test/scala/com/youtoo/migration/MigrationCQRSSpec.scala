package com.youtoo
package migration

import cats.implicits.*

import zio.prelude.*
import zio.test.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*
import zio.jdbc.*

import com.youtoo.migration.model.*
import com.youtoo.migration.service.*
import com.youtoo.migration.store.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.service.*
import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.domain.*

import com.youtoo.cqrs.service.postgres.*

object MigrationCQRSSpec extends ZIOSpecDefault {

  def spec = suite("MigrationCQRSSpec")(
    test("should add command") {
      check(keyGen, migrationCommandGen) { case (id, cmd) =>
        val eventStoreEnv = MockMigrationEventStore.Save(
          equalTo((id, anything)),
          value(1L),
        )

        (for {

          _ <- MigrationCQRS.add(id, cmd)

        } yield assertCompletes).provide(
          (
            eventStoreEnv.toLayer ++ ZConnectionMock
              .pool() ++ MigrationCheckpointerMock.empty ++ MigrationProviderMock.empty ++ MockSnapshotStore.empty ++ SnapshotStrategy
              .live()
          ) >>> MigrationCQRS.live(),
        )
      }

    },
    test("should load migration") {
      check(
        keyGen,
        Gen.option(migrationGen),
        Gen.option(versionGen),
        validMigrationEventSequence,
        Gen.int(0, 12),
      ) { case (id, migration, version, events, amount) =>
        val chs = NonEmptyList(events.head, events.tail.take(amount)*)
        val sendSnapshot = chs.size >= 10
        val maxChange = chs.maxBy(_.version)

        val (key, deps) = (migration, version).tupled match {
          case None =>
            val in = summon[MigrationEventHandler].applyEvents(chs)

            val migrationServiceEnv =
              if sendSnapshot then
                MigrationServiceMock
                  .Save(
                    equalTo(in),
                    value(1L),
                  )
                  .toLayer
              else MigrationServiceMock.empty

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

            val providerEnv = MigrationProviderMock.Load(
              equalTo(id),
              value(None),
            )

            val eventStoreReadEnv = MockMigrationEventStore.ReadEvents.Full(
              equalTo(id),
              value(chs.some),
            )

            val checkpointEnv =
              if sendSnapshot then
                MigrationCheckpointerMock
                  .Save(
                    equalTo(migration),
                    unit,
                  )
                  .toLayer
              else MigrationCheckpointerMock.empty

            (
              id,
              migrationServiceEnv ++ snapshotStoreReadEnv.toLayer ++ snapshotStoreSaveEnv ++ providerEnv.toLayer ++ checkpointEnv ++ eventStoreReadEnv.toLayer,
            )

          case Some((in @ Migration(id, _, _), v)) =>
            val migrationServiceEnv =
              if sendSnapshot then
                MigrationServiceMock
                  .Save(
                    equalTo(in),
                    value(1L),
                  )
                  .toLayer
              else MigrationServiceMock.empty

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

            val providerEnv = MigrationProviderMock.Load(
              equalTo(id.asKey),
              value(in.some),
            )

            val eventStoreReadEnv = MockMigrationEventStore.ReadEvents.Snapshot(
              equalTo((id.asKey, v)),
              value(chs.some),
            )

            val checkpointEnv =
              if sendSnapshot then
                MigrationCheckpointerMock
                  .Save(
                    equalTo(migration),
                    unit,
                  )
                  .toLayer
              else MigrationCheckpointerMock.empty

            (
              id.asKey,
              migrationServiceEnv ++ snapshotStoreReadEnv.toLayer ++ snapshotStoreSaveEnv ++ providerEnv.toLayer ++ checkpointEnv ++ eventStoreReadEnv.toLayer,
            )

        }

        (for {

          _ <- MigrationCQRS.load(key)

        } yield assertCompletes)
          .provide((deps ++ ZConnectionMock.pool() ++ SnapshotStrategy.live()) >>> MigrationCQRS.live())
      }

    },
  ) @@ TestAspect.withLiveClock
}

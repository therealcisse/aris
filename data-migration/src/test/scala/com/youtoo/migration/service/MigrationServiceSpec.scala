package com.youtoo
package migration
package service

import cats.implicits.*

import zio.prelude.*
import zio.test.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*
import zio.jdbc.*
import zio.mock.*

import com.youtoo.cqrs.service.*
import com.youtoo.cqrs.service.postgres.*

import com.youtoo.migration.repository.*

import com.youtoo.migration.model.*
import com.youtoo.cqrs.*
import com.youtoo.migration.store.*
import com.youtoo.cqrs.store.*

object MigrationServiceSpec extends MockSpecDefault {
  inline val Threshold = 10

  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    Log.layer >>> testEnvironment ++ Runtime.setConfigProvider(
      ConfigProvider.fromMap(Map("Migration.snapshots.threshold" -> s"$Threshold")),
    )

  def spec = suite("MigrationServiceSpec")(
    test("should load migration") {
      check(
        Gen.option(migrationGen),
        Gen.option(versionGen),
        validMigrationEventSequence,
      ) { case (migration, version, events) =>
        val maxChange = events.toList.maxBy(_.version)

        val eventHandler = summon[EventHandler[MigrationEvent, Migration]]

        val (id, deps) = (migration, version).tupled match {
          case None =>
            val sendSnapshot = events.size >= Threshold
            val in @ Migration(id, _, _) = eventHandler.applyEvents(events)

            val loadMock = MigrationRepositoryMock.Load(equalTo(id), value(None))
            val readSnapshotMock = MockSnapshotStore.ReadSnapshot(equalTo(id), value(None))
            val saveMock = MigrationRepositoryMock.Save(equalTo(in), value(1L))
            val saveSnapshotMock = MockSnapshotStore.SaveSnapshot(equalTo((id.asKey, maxChange.version)), value(1L))
            val readEventsMock = MockMigrationEventStore.ReadEvents.Full(equalTo(id.asKey), value(events.some))

            val layers =
              if sendSnapshot then
                ((loadMock ++ readSnapshotMock) || (readSnapshotMock ++ loadMock)) ++ readEventsMock ++ ((saveMock ++ saveSnapshotMock) || (saveSnapshotMock ++ saveMock))
              else ((loadMock ++ readSnapshotMock) || (readSnapshotMock ++ loadMock)) ++ readEventsMock

            (
              id,
              layers.toLayer,
            )

          case Some((in @ Migration(id, _, _), v)) =>
            val sendSnapshot = (events.size - 1) >= Threshold

            val es = NonEmptyList.fromIterableOption(events.tail)

            val inn = es match {
              case None => in
              case Some(nel) => eventHandler.applyEvents(in, nel)
            }

            val readSnapshotMock = MockSnapshotStore.ReadSnapshot(equalTo(id.asKey), value(v.some))
            val loadMock = MigrationRepositoryMock.Load(equalTo(id), value(in.some))
            val saveMock = MigrationRepositoryMock.Save(equalTo(inn), value(1L))
            val saveSnapshotMock = MockSnapshotStore.SaveSnapshot(equalTo((id.asKey, maxChange.version)), value(1L))
            val readEventsMock = MockMigrationEventStore.ReadEvents.Snapshot(equalTo((id.asKey, v)), value(es))

            val layers =
              if sendSnapshot then
                ((readSnapshotMock ++ loadMock) || (loadMock ++ readSnapshotMock)) ++ readEventsMock ++ ((saveMock ++ saveSnapshotMock) || (saveSnapshotMock ++ saveMock))
              else ((readSnapshotMock ++ loadMock) || (loadMock ++ readSnapshotMock)) ++ readEventsMock

            (
              id,
              layers.toLayer,
            )

        }

        (for {

          _ <- MigrationService.load(id)

        } yield assertCompletes)
          .provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
            (deps ++ SnapshotStrategy.live()) >>> MigrationService.live(),
          )
      }

    },
    test("save returns expected result using MigrationRepository") {
      check(migrationGen) { case migration =>
        val expected = 1L

        val mockEnv = MigrationRepositoryMock.Save(
          equalTo(migration),
          value(expected),
        )

        (for {
          effect <- atomically(MigrationService.save(migration))
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            MigrationRepository & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MigrationService,
          ](
            PostgresCQRSPersistence.live(),
            MigrationEventStore.live(),
            SnapshotStore.live(),
            SnapshotStrategy.live(),
            MigrationService.live(),
          ),
        )

      }
    },
    test("load many returns expected result using MigrationRepository") {
      check(Gen.some(keyGen), Gen.long, keyGen) { case (key, limit, id) =>
        val expected = Chunk(id)

        val mockEnv = MigrationRepositoryMock.LoadMany(
          equalTo((key, limit)),
          value(expected),
        )

        (for {
          effect <- atomically(MigrationService.loadMany(offset = key, limit = limit))
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
          mockEnv.toLayer >>> ZLayer.makeSome[
            MigrationRepository & ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing,
            MigrationService,
          ](
            PostgresCQRSPersistence.live(),
            MigrationEventStore.live(),
            SnapshotStore.live(),
            SnapshotStrategy.live(),
            MigrationService.live(),
          ),
        )

      }
    },
  ).provideSomeLayerShared(
    ZConnectionMock.pool() ++ (zio.telemetry.opentelemetry.OpenTelemetry.contextZIO >>> tracingMockLayer()),
  )

}

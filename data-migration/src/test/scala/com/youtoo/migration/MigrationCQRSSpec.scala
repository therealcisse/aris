package com.youtoo
package migration

import zio.test.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*
import zio.jdbc.*
import zio.mock.*

import com.youtoo.migration.model.*
import com.youtoo.migration.store.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.service.*
import com.youtoo.cqrs.store.*

import com.youtoo.cqrs.service.postgres.*
import com.youtoo.cqrs.domain.*

object MigrationCQRSSpec extends MockSpecDefault {
  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    testEnvironment ++ Runtime.setConfigProvider(ConfigProvider.fromMap(Map("Migration.snapshots.threshold" -> "10")))

  def spec = suite("MigrationCQRSSpec")(
    test("should add command") {
      check(keyGen, migrationCommandGen) { case (id, cmd) =>
        val Cmd = summon[CmdHandler[MigrationCommand, MigrationEvent]]

        inline def isPayload(key: Key, payload: MigrationEvent) =
          assertion[(Key, Change[MigrationEvent])]("MigrationCQRS.isPayload") { case (id, ch) =>
            id == key && ch.payload == payload
          }

        val evnts = Cmd.applyCmd(cmd)

        val zero = MockMigrationEventStore
          .Save(
            isPayload(id, evnts.head),
            value(1L),
          )
          .toLayer

        val eventStoreEnv = evnts.tail.foldLeft(zero) { case (ass, e) =>
          ass ++ MockMigrationEventStore.Save(isPayload(id, e), value(1L)).toLayer
        }

        val layer = eventStoreEnv ++ MockSnapshotStore.empty ++ SnapshotStrategy.live() >>> MigrationCQRS.live()

        (for {

          _ <- MigrationCQRS.add(id, cmd)

        } yield assertCompletes).provideSomeLayer[ZConnectionPool](layer)

      }

    },
  ).provideLayerShared(ZConnectionMock.pool())

}

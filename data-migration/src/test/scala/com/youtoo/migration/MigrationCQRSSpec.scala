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

object MigrationCQRSSpec extends MockSpecDefault {

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
              .pool() ++ MockSnapshotStore.empty ++ SnapshotStrategy
              .live()
          ) >>> MigrationCQRS.live(),
        )
      }

    },
  ) @@ TestAspect.withLiveClock
}

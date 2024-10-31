package com.youtoo
package ingestion

import zio.test.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*
import zio.jdbc.*
import zio.mock.*

import com.youtoo.cqrs.*
import com.youtoo.ingestion.model.*
import com.youtoo.ingestion.store.*

import com.youtoo.cqrs.service.*
import com.youtoo.cqrs.store.*

import com.youtoo.cqrs.service.postgres.*

object IngestionCQRSSpec extends MockSpecDefault {

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
              .pool() ++ MockSnapshotStore.empty ++ SnapshotStrategy
              .live()
          ) >>> IngestionCQRS.live(),
        )
      }

    },
  ) @@ TestAspect.withLiveClock
}

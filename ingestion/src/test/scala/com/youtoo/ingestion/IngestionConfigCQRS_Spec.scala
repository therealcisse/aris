package com.youtoo
package ingestion

import zio.test.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*
import zio.jdbc.*
import zio.mock.*

import com.youtoo.postgres.*
import com.youtoo.cqrs.*
import com.youtoo.ingestion.model.*
import com.youtoo.ingestion.store.*

import com.youtoo.cqrs.store.*

import com.youtoo.cqrs.domain.*

object IngestionConfigCQRS_Spec extends MockSpecDefault {
  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    Log.layer >>> testEnvironment ++ Runtime.setConfigProvider(
      ConfigProvider.fromMap(Map("IngestionConfig.snapshots.threshold" -> "10")),
    )

  def spec = suite("IngestionConfigCQRS_Spec")(
    test("should add command") {
      check(keyGen, ingestionConfigCommandGen) { case (id, cmd) =>
        val Cmd = summon[CmdHandler[IngestionConfigCommand, IngestionConfigEvent]]

        inline def isPayload(key: Key, payload: IngestionConfigEvent) =
          assertion[(Key, Change[IngestionConfigEvent])]("IngestionConfigCQRS.isPayload") { case (id, ch) =>
            id == key && ch.payload == payload
          }

        val evnts = Cmd.applyCmd(cmd)

        val zero = IngestionConfigEventStoreMock
          .SaveEvent(
            isPayload(id, evnts.head),
            value(1L),
          )
          .toLayer

        val eventStoreEnv = evnts.tail.foldLeft(zero) { case (ass, e) =>
          ass ++ IngestionConfigEventStoreMock.SaveEvent(isPayload(id, e), value(1L)).toLayer
        }

        val layer = eventStoreEnv ++ MockSnapshotStore.empty ++ SnapshotStrategy.live() >>> IngestionConfigCQRS.live()

        (for {

          _ <- IngestionConfigCQRS.add(id, cmd)

        } yield assertCompletes).provideSomeLayer[ZConnectionPool](layer)
      }

    },
  ).provideLayerShared(ZConnectionMock.pool())
}

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
import com.youtoo.cqrs.domain.*

object IngestionCQRSSpec extends MockSpecDefault {
  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    testEnvironment ++ Runtime.setConfigProvider(ConfigProvider.fromMap(Map("Ingestion.snapshots.threshold" -> "10")))

  def spec = suite("IngestionCQRSSpec")(
    test("should add command") {
      check(keyGen, ingestionCommandGen) { case (id, cmd) =>
        val Cmd = summon[CmdHandler[IngestionCommand, IngestionEvent]]

        inline def isArg(key: Key, payload: IngestionEvent) = assertion[(Key, Change[IngestionEvent])]("isArg") {
          case (id, ch) => id == key && ch.payload == payload
        }

        val evnts = Cmd.applyCmd(cmd)

        val zero = MockIngestionEventStore
          .Save(
            isArg(id, evnts.head),
            value(1L),
          )
          .toLayer

        val eventStoreEnv = evnts.tail.foldLeft(zero) { case (ass, e) =>
          ass ++ MockIngestionEventStore.Save(isArg(id, e), value(1L)).toLayer
        }

        val layer = eventStoreEnv ++ MockSnapshotStore.empty ++ SnapshotStrategy.live() >>> IngestionCQRS.live()

        (for {

          _ <- IngestionCQRS.add(id, cmd)

        } yield assertCompletes).provideSomeLayer[ZConnectionPool](layer)
      }

    },
  ).provideLayerShared(ZConnectionMock.pool())
}

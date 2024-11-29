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

import com.youtoo.cqrs.domain.*

object FileCQRSSpec extends MockSpecDefault {
  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    Log.layer >>> testEnvironment

  def spec = suite("FileCQRSSpec")(
    test("should add command") {
      check(keyGen, fileCommandGen) { case (id, cmd) =>
        val Cmd = summon[CmdHandler[FileCommand, FileEvent]]

        inline def isPayload(key: Key, payload: FileEvent) =
          assertion[(Key, Change[FileEvent])]("FileCQRS.isPayload") { case (id, ch) =>
            id == key && ch.payload == payload
          }

        val evnts = Cmd.applyCmd(cmd)

        val zero = MockFileEventStore
          .Save(
            isPayload(id, evnts.head),
            value(1L),
          )
          .toLayer

        val eventStoreEnv = evnts.tail.foldLeft(zero) { case (ass, e) =>
          ass ++ MockFileEventStore.Save(isPayload(id, e), value(1L)).toLayer
        }

        val layer = eventStoreEnv ++ MockSnapshotStore.empty >>> FileCQRS.live()

        (for {

          _ <- FileCQRS.add(id, cmd)

        } yield assertCompletes).provideSomeLayer[ZConnectionPool](layer)
      }

    },
  ).provideLayerShared(ZConnectionMock.pool())
}

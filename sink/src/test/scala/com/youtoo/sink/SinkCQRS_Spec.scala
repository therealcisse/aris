package com.youtoo
package sink

import zio.test.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*
import zio.jdbc.*
import zio.mock.*

import com.youtoo.postgres.*
import com.youtoo.cqrs.*
import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.domain.*

import com.youtoo.sink.model.*
import com.youtoo.sink.store.*

object SinkCQRS_Spec extends MockSpecDefault {
  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    Log.layer ++ testEnvironment

  def spec = suite("SinkCQRS_Spec")(
    test("should add command") {
      check(keyGen, sinkCommandGen) { (id, cmd) =>
        val Cmd = summon[CmdHandler[SinkCommand, SinkEvent]]

        inline def isPayload(key: Key, payload: SinkEvent) =
          assertion[(Key, Change[SinkEvent])]("SinkCQRS.isPayload") { case (id, ch) =>
            id == key && ch.payload == payload
          }

        val evnts = Cmd.applyCmd(cmd)

        val zero = SinkEventStoreMock
          .SaveEvent(
            isPayload(id, evnts.head),
            value(1L),
          )
          .toLayer

        val eventStoreEnv = evnts.tail.foldLeft(zero) { case (ass, e) =>
          ass ++ SinkEventStoreMock.SaveEvent(isPayload(id, e), value(1L)).toLayer
        }

        val layer = eventStoreEnv ++ MockSnapshotStore.empty >>> SinkCQRS.live()

        (for {
          _ <- SinkCQRS.add(id, cmd)
        } yield assertCompletes).provideSomeLayer[ZConnectionPool](layer)
      }
    },
  ).provideLayerShared(ZConnectionMock.pool())
}

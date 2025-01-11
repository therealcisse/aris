package com.youtoo
package source

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

import com.youtoo.source.model.*
import com.youtoo.source.store.*

object SourceCQRS_Spec extends MockSpecDefault {
  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    Log.layer ++ testEnvironment

  def spec = suite("SourceCQRS_Spec")(
    test("should add command") {
      check(keyGen, sourceCommandGen) { (id, cmd) =>
        val Cmd = summon[CmdHandler[SourceCommand, SourceEvent]]

        inline def isPayload(key: Key, payload: SourceEvent) =
          assertion[(Key, Change[SourceEvent])]("SourceCQRS.isPayload") { case (id, ch) =>
            id == key && ch.payload == payload
          }

        val evnts = Cmd.applyCmd(cmd)

        val zero = SourceEventStoreMock
          .SaveEvent(
            isPayload(id, evnts.head),
            value(1L),
          )
          .toLayer

        val eventStoreEnv = evnts.tail.foldLeft(zero) { case (ass, e) =>
          ass ++ SourceEventStoreMock.SaveEvent(isPayload(id, e), value(1L)).toLayer
        }

        val layer = eventStoreEnv ++ MockSnapshotStore.empty >>> SourceCQRS.live()

        (for {
          _ <- SourceCQRS.add(id, cmd)
        } yield assertCompletes).provideSomeLayer[ZConnectionPool](layer)
      }
    },
  ).provideLayerShared(ZConnectionMock.pool())
}

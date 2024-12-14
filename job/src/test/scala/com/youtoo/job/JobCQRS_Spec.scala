package com.youtoo
package job

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
import com.youtoo.job.model.*
import com.youtoo.job.store.*

object JobCQRS_Spec extends MockSpecDefault {
  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    Log.layer ++ testEnvironment

  def spec = suite("JobCQRS_Spec")(
    test("should add command") {
      check(keyGen, jobCommandGen) { (id, cmd) =>
        val Cmd = summon[CmdHandler[JobCommand, JobEvent]]

        inline def isPayload(key: Key, payload: JobEvent) =
          assertion[(Key, Change[JobEvent])]("JobCQRS.isPayload") { case (id, ch) =>
            id == key && ch.payload == payload
          }

        val evnts = Cmd.applyCmd(cmd)

        val zero = JobEventStoreMock
          .SaveEvent(
            isPayload(id, evnts.head),
            value(1L),
          )
          .toLayer

        val eventStoreEnv = evnts.tail.foldLeft(zero) { case (ass, e) =>
          ass ++ JobEventStoreMock.SaveEvent(isPayload(id, e), value(1L)).toLayer
        }

        val layer = eventStoreEnv ++ MockSnapshotStore.empty >>> JobCQRS.live()

        (for {
          _ <- JobCQRS.add(id, cmd)
        } yield assertCompletes).provideSomeLayer[ZConnectionPool](layer)
      }
    },
  ).provideLayerShared(ZConnectionMock.pool())
}

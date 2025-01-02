package com.youtoo
package mail

import zio.test.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*
import zio.jdbc.*
import zio.mock.*

import com.youtoo.postgres.*
import com.youtoo.cqrs.*
import com.youtoo.mail.model.*
import com.youtoo.mail.store.*

import com.youtoo.cqrs.store.*

import com.youtoo.cqrs.domain.*

object DownloadCQRS_Spec extends MockSpecDefault {
  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    Log.layer >>> testEnvironment ++ Runtime.setConfigProvider(
      ConfigProvider.fromMap(Map("Download.snapshots.threshold" -> "10")),
    )

  def spec = suite("DownloadCQRS_Spec")(
    test("should add command") {
      check(keyGen, downloadCommandGen) { case (id, cmd) =>
        val Cmd = summon[CmdHandler[DownloadCommand, DownloadEvent]]

        inline def isPayload(key: Key, payload: DownloadEvent) =
          assertion[(Key, Change[DownloadEvent])]("DownloadCQRS.isPayload") { case (id, ch) =>
            id == key && ch.payload == payload
          }

        val evnts = Cmd.applyCmd(cmd)

        val zero = MockDownloadEventStore
          .Save(
            isPayload(id, evnts.head),
            value(1L),
          )
          .toLayer

        val eventStoreEnv = evnts.tail.foldLeft(zero) { case (ass, e) =>
          ass ++ MockDownloadEventStore.Save(isPayload(id, e), value(1L)).toLayer
        }

        val layer = eventStoreEnv ++ MockSnapshotStore.empty ++ SnapshotStrategy.live() >>> DownloadCQRS.live()

        (for {

          _ <- DownloadCQRS.add(id, cmd)

        } yield assertCompletes).provideSomeLayer[ZConnectionPool](layer)
      }

    },
  ).provideLayerShared(ZConnectionMock.pool())
}

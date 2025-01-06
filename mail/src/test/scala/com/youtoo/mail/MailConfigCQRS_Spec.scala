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

object MailConfigCQRS_Spec extends MockSpecDefault {
  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    Log.layer >>> testEnvironment ++ Runtime.setConfigProvider(
      ConfigProvider.fromMap(Map("MailConfig.snapshots.threshold" -> "10")),
    )

  def spec = suite("MailConfigCQRS_Spec")(
    test("should add command") {
      check(keyGen, mailConfigCommandGen) { case (id, cmd) =>
        val Cmd = summon[CmdHandler[MailConfigCommand, MailConfigEvent]]

        inline def isPayload(key: Key, payload: MailConfigEvent) =
          assertion[(Key, Change[MailConfigEvent])]("MailConfigCQRS.isPayload") { case (id, ch) =>
            id == key && ch.payload == payload
          }

        val evnts = Cmd.applyCmd(cmd)

        val zero = MailConfigEventStoreMock
          .SaveEvent(
            isPayload(id, evnts.head),
            value(1L),
          )
          .toLayer

        val eventStoreEnv = evnts.tail.foldLeft(zero) { case (ass, e) =>
          ass ++ MailConfigEventStoreMock.SaveEvent(isPayload(id, e), value(1L)).toLayer
        }

        val layer = eventStoreEnv ++ MockSnapshotStore.empty ++ SnapshotStrategy.live() >>> MailConfigCQRS.live()

        (for {

          _ <- MailConfigCQRS.add(id, cmd)

        } yield assertCompletes).provideSomeLayer[ZConnectionPool](layer)
      }

    },
  ).provideLayerShared(ZConnectionMock.pool())
}

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

object MailCQRS_Spec extends MockSpecDefault {
  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    Log.layer >>> testEnvironment ++ Runtime.setConfigProvider(
      ConfigProvider.fromMap(Map("Mail.snapshots.threshold" -> "10")),
    )

  def spec = suite("MailCQRS_Spec")(
    test("should add command") {
      check(keyGen, mailCommandGen) { case (id, cmd) =>
        val Cmd = summon[CmdHandler[MailCommand, MailEvent]]

        inline def isPayload(key: Key, payload: MailEvent) =
          assertion[(Key, Change[MailEvent])]("MailCQRS.isPayload") { case (id, ch) =>
            id == key && ch.payload == payload
          }

        val evnts = Cmd.applyCmd(cmd)

        val zero = MockMailEventStore
          .Save(
            isPayload(id, evnts.head),
            value(1L),
          )
          .toLayer

        val eventStoreEnv = evnts.tail.foldLeft(zero) { case (ass, e) =>
          ass ++ MockMailEventStore.Save(isPayload(id, e), value(1L)).toLayer
        }

        val layer = eventStoreEnv ++ MockSnapshotStore.empty ++ SnapshotStrategy.live() >>> MailCQRS.live()

        (for {

          _ <- MailCQRS.add(id, cmd)

        } yield assertCompletes).provideSomeLayer[ZConnectionPool](layer)
      }

    },
  ).provideLayerShared(ZConnectionMock.pool())
}

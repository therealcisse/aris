package com.youtoo
package mail

import zio.*
import zio.jdbc.*

import com.youtoo.mail.model.*
import com.youtoo.mail.store.*
import com.youtoo.postgres.*
import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.*

import com.youtoo.cqrs.Codecs.*

trait MailConfigCQRS extends CQRS[MailConfigEvent, MailConfigCommand] {}

object MailConfigCQRS {

  inline def add(id: Key, cmd: MailConfigCommand)(using
    Cmd: CmdHandler[MailConfigCommand, MailConfigEvent],
  ): RIO[MailConfigCQRS, Unit] =
    ZIO.serviceWithZIO[MailConfigCQRS](_.add(id, cmd))

  def live(): ZLayer[ZConnectionPool & MailConfigEventStore, Throwable, MailConfigCQRS] =
    ZLayer.fromFunction(LiveMailConfigCQRS.apply)

  class LiveMailConfigCQRS(pool: ZConnectionPool, eventStore: MailConfigEventStore) extends MailConfigCQRS {
    def add(id: Key, cmd: MailConfigCommand): Task[Unit] =
      atomically {
        val evnts = CmdHandler.applyCmd(cmd)

        ZIO.foreachDiscard(evnts) { payload =>
          for {
            version <- Version.gen
            ch = Change(version, payload)
            _ <- eventStore.save(id = id, ch)
          } yield ()
        }
      }.provideEnvironment(ZEnvironment(pool))
  }

}

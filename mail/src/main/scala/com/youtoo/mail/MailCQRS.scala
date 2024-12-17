package com.youtoo
package mail

import zio.*

import zio.jdbc.*
import zio.prelude.*

import com.youtoo.cqrs.Codecs.*

import com.youtoo.postgres.*
import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.mail.model.*
import com.youtoo.mail.store.*

trait MailCQRS extends CQRS[MailEvent, MailCommand] {}

object MailCQRS {

  inline def add(id: Key, cmd: MailCommand)(using
    Cmd: CmdHandler[MailCommand, MailEvent],
  ): RIO[MailCQRS, Unit] = ZIO.serviceWithZIO[MailCQRS](_.add(id, cmd))

  def live(): ZLayer[
    ZConnectionPool & MailEventStore,
    Throwable,
    MailCQRS,
  ] =
    ZLayer.fromFunction(LiveMailCQRS.apply)

  class LiveMailCQRS(
    pool: ZConnectionPool,
    eventStore: MailEventStore,
  ) extends MailCQRS {

    def add(id: Key, cmd: MailCommand): Task[Unit] =
      atomically {
        val evnts = CmdHandler.applyCmd(cmd)

        ZIO.foreachDiscard(evnts) { payload =>
          for {
            version <- Version.gen
            ch = Change(version = version, payload = payload)
            _ <- eventStore.save(id = id, ch)
          } yield ()
        }
      }.provideEnvironment(ZEnvironment(pool))
  }

}

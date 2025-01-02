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

trait DownloadCQRS extends CQRS[DownloadEvent, DownloadCommand] {}

object DownloadCQRS {

  inline def add(id: Key, cmd: DownloadCommand)(using
    Cmd: CmdHandler[DownloadCommand, DownloadEvent],
  ): RIO[DownloadCQRS, Unit] = ZIO.serviceWithZIO[DownloadCQRS](_.add(id, cmd))

  def live(): ZLayer[
    ZConnectionPool & DownloadEventStore,
    Throwable,
    DownloadCQRS,
  ] =
    ZLayer.fromFunction(LiveDownloadCQRS.apply)

  class LiveDownloadCQRS(
    pool: ZConnectionPool,
    eventStore: DownloadEventStore,
  ) extends DownloadCQRS {

    def add(id: Key, cmd: DownloadCommand): Task[Unit] =
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

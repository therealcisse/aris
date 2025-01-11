package com.youtoo
package source

import zio.*
import zio.jdbc.*

import com.youtoo.source.model.*
import com.youtoo.source.store.*

import com.youtoo.postgres.*
import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.*

import com.youtoo.cqrs.Codecs.*

trait SourceCQRS extends CQRS[SourceEvent, SourceCommand] {}

object SourceCQRS {

  inline def add(id: Key, cmd: SourceCommand)(using
    Cmd: CmdHandler[SourceCommand, SourceEvent],
  ): RIO[SourceCQRS, Unit] =
    ZIO.serviceWithZIO[SourceCQRS](_.add(id, cmd))

  def live(): ZLayer[ZConnectionPool & SourceEventStore, Throwable, SourceCQRS] =
    ZLayer.fromFunction(LiveSourceCQRS.apply)

  class LiveSourceCQRS(pool: ZConnectionPool, eventStore: SourceEventStore) extends SourceCQRS {
    def add(id: Key, cmd: SourceCommand): Task[Unit] =
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

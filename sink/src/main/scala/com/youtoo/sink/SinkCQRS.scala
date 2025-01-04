package com.youtoo
package sink

import zio.*
import zio.jdbc.*

import com.youtoo.sink.model.*
import com.youtoo.sink.store.*

import com.youtoo.postgres.*
import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.*

import com.youtoo.cqrs.Codecs.*

trait SinkCQRS extends CQRS[SinkEvent, SinkCommand] {}

object SinkCQRS {

  inline def add(id: Key, cmd: SinkCommand)(using Cmd: CmdHandler[SinkCommand, SinkEvent]): RIO[SinkCQRS, Unit] =
    ZIO.serviceWithZIO[SinkCQRS](_.add(id, cmd))

  def live(): ZLayer[ZConnectionPool & SinkEventStore, Throwable, SinkCQRS] =
    ZLayer.fromFunction(LiveSinkCQRS.apply)

  class LiveSinkCQRS(pool: ZConnectionPool, eventStore: SinkEventStore) extends SinkCQRS {
    def add(id: Key, cmd: SinkCommand): Task[Unit] =
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

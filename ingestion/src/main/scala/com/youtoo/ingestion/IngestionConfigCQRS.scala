package com.youtoo
package ingestion

import zio.*
import zio.jdbc.*

import com.youtoo.ingestion.model.*
import com.youtoo.ingestion.store.*
import com.youtoo.postgres.*
import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.*

import com.youtoo.cqrs.Codecs.*

trait IngestionConfigCQRS extends CQRS[IngestionConfigEvent, IngestionConfigCommand] {}

object IngestionConfigCQRS {

  inline def add(id: Key, cmd: IngestionConfigCommand)(using
    Cmd: CmdHandler[IngestionConfigCommand, IngestionConfigEvent],
  ): RIO[IngestionConfigCQRS, Unit] =
    ZIO.serviceWithZIO[IngestionConfigCQRS](_.add(id, cmd))

  def live(): ZLayer[ZConnectionPool & IngestionConfigEventStore, Throwable, IngestionConfigCQRS] =
    ZLayer.fromFunction(LiveIngestionConfigCQRS.apply)

  class LiveIngestionConfigCQRS(pool: ZConnectionPool, eventStore: IngestionConfigEventStore)
      extends IngestionConfigCQRS {
    def add(id: Key, cmd: IngestionConfigCommand): Task[Unit] =
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

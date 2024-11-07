package com.youtoo
package ingestion

import zio.*

import zio.jdbc.*
import zio.prelude.*

import com.youtoo.cqrs.Codecs.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.ingestion.model.*
import com.youtoo.cqrs.service.*
import com.youtoo.ingestion.store.*

import zio.metrics.*

import java.time.temporal.ChronoUnit

trait FileCQRS extends CQRS[FileEvent, FileCommand] {}

object FileCQRS {

  object metrics {
    val addition = Metric.timer(
      "File_addition_duration",
      chronoUnit = ChronoUnit.MILLIS,
      boundaries = Chunk.iterate(1.0, 10)(_ + 1.0),
    )

  }

  inline def add(id: Key, cmd: FileCommand)(using
    Cmd: FileCommandHandler,
  ): RIO[FileCQRS, Unit] = ZIO.serviceWithZIO[FileCQRS](_.add(id, cmd)) @@ metrics.addition.trackDuration

  def live(): ZLayer[
    ZConnectionPool & FileEventStore,
    Throwable,
    FileCQRS,
  ] =
    ZLayer.fromFunction(LiveFileCQRS.apply)

  class LiveFileCQRS(
    pool: ZConnectionPool,
    eventStore: FileEventStore,
  ) extends FileCQRS {

    def add(id: Key, cmd: FileCommand): Task[Unit] =
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

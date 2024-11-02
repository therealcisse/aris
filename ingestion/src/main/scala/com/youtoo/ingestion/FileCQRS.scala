package com.youtoo
package ingestion

import zio.*

import zio.jdbc.*
import zio.prelude.*

import com.youtoo.cqrs.Codecs.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.store.*
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
    ZConnectionPool & FileEventStore & SnapshotStore,
    Throwable,
    FileCQRS,
  ] =
    ZLayer.fromFunction {
      (
        eventStore: FileEventStore,
        snapshotStore: SnapshotStore,
        pool: ZConnectionPool,
      ) =>
        new FileCQRS {
          private val Cmd = summon[CmdHandler[FileCommand, FileEvent]]

          def add(id: Key, cmd: FileCommand): Task[Unit] =
            atomically {
              val evnts = Cmd.applyCmd(cmd)

              ZIO.foreachDiscard(evnts) { e =>
                for {
                  version <- Version.gen
                  ch = Change(version = version, payload = e)
                  _ <- eventStore.save(id = id, ch)
                } yield ()
              }
            }.provideEnvironment(ZEnvironment(pool))

        }

    }

}

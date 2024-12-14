package com.youtoo
package job

import com.youtoo.cqrs.Codecs.given

import zio.*
import zio.jdbc.*
import com.youtoo.postgres.*
import com.youtoo.cqrs.*
import com.youtoo.job.store.*
import com.youtoo.cqrs.domain.*
import com.youtoo.job.model.*

trait JobCQRS extends CQRS[JobEvent, JobCommand] {}

object JobCQRS {

  inline def add(id: Key, cmd: JobCommand)(using Cmd: JobCommandHandler): RIO[JobCQRS, Unit] =
    ZIO.serviceWithZIO[JobCQRS](_.add(id, cmd))

  def live(): ZLayer[ZConnectionPool & JobEventStore, Throwable, JobCQRS] =
    ZLayer.fromFunction(LiveJobCQRS.apply)

  class LiveJobCQRS(pool: ZConnectionPool, eventStore: JobEventStore) extends JobCQRS {
    def add(id: Key, cmd: JobCommand): Task[Unit] =
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

package com.youtoo
package migration

import zio.*

import zio.jdbc.*
import zio.prelude.*

import com.youtoo.cqrs.Codecs.*

import com.youtoo.cqrs.domain.*
import com.youtoo.migration.model.*
import com.youtoo.cqrs.*
import com.youtoo.cqrs.service.*
import com.youtoo.migration.store.*

trait MigrationCQRS extends CQRS[MigrationEvent, MigrationCommand] {}

object MigrationCQRS {

  inline def add(id: Key, cmd: MigrationCommand): RIO[MigrationCQRS, Unit] =
    ZIO.serviceWithZIO[MigrationCQRS](_.add(id, cmd))

  def live(): ZLayer[
    ZConnectionPool & MigrationEventStore,
    Throwable,
    MigrationCQRS,
  ] =
    ZLayer.fromFunction {
      (
        pool: ZConnectionPool,
        eventStore: MigrationEventStore,
      ) =>
        new LiveMigrationCQRS(
          pool,
          eventStore,
        )

    }

  class LiveMigrationCQRS(
    pool: ZConnectionPool,
    eventStore: MigrationEventStore,
  ) extends MigrationCQRS {

    def add(id: Key, cmd: MigrationCommand): Task[Unit] =
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

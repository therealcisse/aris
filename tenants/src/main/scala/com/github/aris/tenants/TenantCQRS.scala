package com.github
package aris
package tenants

import aris.*
import aris.store.*

import zio.*
import zio.prelude.*

trait TenantCQRS extends CQRS[TenantEvent, TenantCommand]

object TenantCQRS {
  def live(): ZLayer[TenantEventStore, Nothing, TenantCQRS] =
    ZLayer.fromFunction(new TenantCQRSLive(_))

  class TenantCQRSLive(store: TenantEventStore) extends TenantCQRS {
    def add(id: Key, cmd: TenantCommand): Task[Unit] =
      for {
        events <- ZIO.succeed(CmdHandler.applyCmd(cmd))
        _ <- ZIO.foreachDiscard(events.toChunk) { ev =>
               for {
                 version <- Version.gen
                 change = Change(version, ev)
                 _ <- store.save(id, change)
               } yield ()
             }
      } yield ()
  }
}

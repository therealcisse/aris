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

trait AuthorizationCQRS extends CQRS[AuthorizationEvent, AuthorizationCommand] {}

object AuthorizationCQRS {

  inline def add(id: Key, cmd: AuthorizationCommand)(using
    Cmd: CmdHandler[AuthorizationCommand, AuthorizationEvent],
  ): RIO[AuthorizationCQRS, Unit] = ZIO.serviceWithZIO[AuthorizationCQRS](_.add(id, cmd))

  def live(): ZLayer[
    ZConnectionPool & AuthorizationEventStore,
    Throwable,
    AuthorizationCQRS,
  ] =
    ZLayer.fromFunction(LiveAuthorizationCQRS.apply)

  class LiveAuthorizationCQRS(
    pool: ZConnectionPool,
    eventStore: AuthorizationEventStore,
  ) extends AuthorizationCQRS {

    def add(id: Key, cmd: AuthorizationCommand): Task[Unit] =
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

package com.youtoo
package mail
package integration

import cats.implicits.*

import zio.*

import com.youtoo.mail.integration.internal.GmailSupport

import com.youtoo.mail.service.*
import com.youtoo.mail.model.*

import com.google.api.services.gmail.Gmail

case class GmailPool(pool: ZKeyedPool[Throwable, MailAccount.Id, Gmail]) {
  export pool.get

}

object GmailPool {

  def live(): ZLayer[Scope & MailService, Throwable, GmailPool] =
    ZLayer.scoped {

      for {
        service <- ZIO.service[MailService]

        pool <- ZKeyedPool.make(
          get = (id: MailAccount.Id) =>
            for {
              state <- service.loadState(id)

              clientInfo <- ZIO.config[GoogleClientInfo]

              gmail <- state.fold(ZIO.fail(IllegalArgumentException("Accounts state not found"))) {
                case Mail(_, _, Authorization.Granted(token, _)) =>
                  GmailSupport.getClient(clientInfo, token)
                case _ => ZIO.fail(IllegalArgumentException("Account not authorized"))
              }

            } yield gmail,
          size = 1,
        )

      } yield GmailPool(pool)
    }

}

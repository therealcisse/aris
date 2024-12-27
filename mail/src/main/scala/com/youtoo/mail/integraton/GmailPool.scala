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
              info <- (
                service.loadAccount(id) <&> service.loadState(id)
              ).map(_.tupled)

              gmail <- info.fold(ZIO.fail(IllegalArgumentException("Accounts state not found"))) {
                case (account, Mail(_, _, Authorization.Granted(token, _))) =>
                  GmailSupport.getClient(account.settings.authConfig.clientInfo, token)
                case _ => ZIO.fail(IllegalArgumentException("Account not authorized"))
              }

            } yield gmail,
          size = 1,
        )

      } yield GmailPool(pool)
    }

}

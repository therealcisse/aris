package com.youtoo
package mail
package integration

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
              account <- service.loadAccount(id)

              gmail <- account.fold(ZIO.fail(IllegalArgumentException("Account not found"))) { account =>
                GmailSupport.authenticate(account.settings.authConfig)
              }

            } yield gmail,
          size = 1,
        )

      } yield GmailPool(pool)
    }

}

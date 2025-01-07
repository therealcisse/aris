package com.youtoo
package mail
package integration

import cats.implicits.*

import zio.*
import zio.prelude.*

import com.youtoo.mail.integration.internal.GmailSupport
import com.google.api.client.http.javanet.NetHttpTransport

import com.youtoo.mail.service.*
import com.youtoo.mail.model.*

import com.google.api.services.gmail.Gmail

case class GmailPool(pool: ZKeyedPool[Throwable, MailAccount.Id, Gmail]) {
  export pool.{get, invalidate}

}

object GmailPool {
  object TTL extends Newtype[Duration] {
    extension (a: Type) def value: Duration = unwrap(a)
  }

  given Config[TTL.Type] = Config.duration("gmailPoolTTL").withDefault(15.minutes).map(TTL(_))

  def live(): ZLayer[Scope & MailService & NetHttpTransport, Throwable, GmailPool] =
    ZLayer.scoped {

      for {
        service <- ZIO.service[MailService]

        poolTTL <- ZIO.config[TTL.Type]

        pool <- ZKeyedPool.make(
          get = (id: MailAccount.Id) =>
            for {
              state <- service.loadState(id)

              clientInfo <- ZIO.config[GoogleClientInfo]

              gmail <- state.fold(ZIO.fail(new IllegalArgumentException("Accounts state not found"))) {
                case Mail(_, _, Authorization.Granted(token, _)) =>
                  GmailSupport.getClient(clientInfo, token)
                case _ => ZIO.fail(new IllegalArgumentException("Account not authorized"))
              }

            } yield gmail,
          range = _ => Range(0, 1),
          timeToLive = _ => poolTTL.value,
        )

      } yield GmailPool(pool)
    }

}

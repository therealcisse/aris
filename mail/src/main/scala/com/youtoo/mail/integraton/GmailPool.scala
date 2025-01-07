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

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*

case class GmailPool(pool: ZKeyedPool[Throwable, MailAccount.Id, Gmail]) {
  export pool.{get, invalidate}

}

object GmailPool {
  object TTL extends Newtype[Duration] {
    extension (a: Type) def value: Duration = unwrap(a)
  }

  given Config[TTL.Type] = Config.duration("gmail_pool_ttl").withDefault(15.minutes).map(TTL(_))

  def live(): ZLayer[Scope & MailService & NetHttpTransport & Tracing, Throwable, GmailPool] =
    ZLayer.scoped {

      for {
        service <- ZIO.service[MailService]

        poolTTL <- ZIO.config[TTL.Type]

        tracing <- ZIO.service[Tracing]

        pool <- ZKeyedPool.make(
          get = (id: MailAccount.Id) =>
            (for {
              state <- service.loadState(id)

              clientInfo <- ZIO.config[GoogleClientInfo]

              gmail <- state.fold(ZIO.fail(new IllegalArgumentException("Accounts state not found"))) {
                case Mail(_, _, Authorization.Granted(token, _)) =>
                  GmailSupport.getClient(clientInfo, token)
                case _ => ZIO.fail(new IllegalArgumentException("Account not authorized"))
              }

            } yield gmail) @@ tracing.aspects.span(
              "GmailPool.get",
              attributes = Attributes(Attribute.long("accountId", id.asKey.value)),
            ),
          range = _ => Range(0, 1),
          timeToLive = _ => poolTTL.value,
        )

      } yield GmailPool(pool)
    }

}

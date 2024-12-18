package com.youtoo
package mail
package integration

import com.youtoo.mail.model.*
import zio.*
import zio.mock.*
import com.google.api.services.gmail.Gmail

object GmailPoolMock extends Mock[GmailPool] {

  object Get extends Effect[MailAccount.Id, Throwable, com.google.api.services.gmail.Gmail]

  val compose: URLayer[Proxy, GmailPool] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new GmailPool(new ZKeyedPool[Throwable, MailAccount.Id, com.google.api.services.gmail.Gmail] {
        def get(key: MailAccount.Id)(implicit
          trace: Trace,
        ): ZIO[Scope, Throwable, com.google.api.services.gmail.Gmail] =
          proxy(Get, key)

        def invalidate(item: Gmail)(implicit trace: Trace): UIO[Unit] = ???
      })
    }
}

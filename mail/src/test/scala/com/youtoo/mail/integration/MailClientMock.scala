package com.youtoo
package mail
package client

import zio.*
import zio.mock.*

import com.youtoo.mail.model.*
import com.youtoo.mail.integration.*

object MailClientMock extends Mock[MailClient] {

  object LoadLabels extends Effect[MailAccount.Id, Throwable, Chunk[MailLabels.LabelInfo]]
  object FetchMails extends Effect[(MailAddress, Option[MailToken]), Throwable, Option[(Chunk[MailData.Id], MailToken)]]
  object LoadMessage extends Effect[(MailAccount.Id, MailData.Id), Throwable, Option[MailData]]

  val compose: URLayer[Proxy, MailClient] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new MailClient {
        def loadLabels(accountKey: MailAccount.Id): RIO[Scope, Chunk[MailLabels.LabelInfo]] =
          proxy(LoadLabels, accountKey)

        def fetchMails(
          address: MailAddress,
          token: Option[MailToken],
        ): RIO[Scope, Option[(Chunk[MailData.Id], MailToken)]] =
          proxy(FetchMails, (address, token))

        def loadMessage(accountKey: MailAccount.Id, id: MailData.Id): RIO[Scope, Option[MailData]] =
          proxy(LoadMessage, (accountKey, id))
      }
    }
}

package com.youtoo
package mail
package client

import zio.*
import zio.mock.*
import zio.prelude.*

import com.youtoo.mail.model.*
import com.youtoo.mail.integration.*

import zio.telemetry.opentelemetry.tracing.Tracing

object MailClientMock extends Mock[MailClient] {

  object LoadLabels extends Effect[MailAccount.Id, Throwable, Chunk[MailLabels.LabelInfo]]
  object FetchMails
      extends Effect[(MailAccount.Id, Option[MailToken], Option[NonEmptySet[MailLabels.LabelKey]]), Throwable, Option[
        (NonEmptyList[MailData.Id], MailToken),
      ]]
  object LoadMessage extends Effect[(MailAccount.Id, MailData.Id), Throwable, Option[MailData]]

  val compose: URLayer[Proxy, MailClient] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new MailClient {
        def loadLabels(accountKey: MailAccount.Id): RIO[Scope, Chunk[MailLabels.LabelInfo]] =
          proxy(LoadLabels, accountKey)

        def fetchMails(
          accountKey: MailAccount.Id,
          token: Option[MailToken],
          labels: Option[NonEmptySet[MailLabels.LabelKey]],
        ): RIO[Tracing, Option[(NonEmptyList[MailData.Id], MailToken)]] =
          proxy(FetchMails, (accountKey, token, labels))

        def loadMessage(accountKey: MailAccount.Id, id: MailData.Id): Task[Option[MailData]] =
          proxy(LoadMessage, (accountKey, id))
      }
    }
}

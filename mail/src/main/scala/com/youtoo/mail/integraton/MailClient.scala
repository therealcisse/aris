package com.youtoo
package mail
package integration

import zio.*

import com.youtoo.mail.integration.internal.GmailSupport

import com.youtoo.mail.service.*
import com.youtoo.mail.model.*

import com.google.api.services.gmail.Gmail
import zio.prelude.*

import scala.jdk.CollectionConverters.*

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*

trait MailClient {
  def loadLabels(accountKey: MailAccount.Id): RIO[Scope, Chunk[MailLabels.LabelInfo]]
  def fetchMails(
    address: MailAddress,
    token: Option[MailToken],
  ): RIO[Scope, (Chunk[MailData.Id], Option[MailToken])]
  def loadMessage(accountKey: MailAccount.Id, id: MailData.Id): RIO[Scope, Option[MailData]]
}

object MailClient {
  object BatchSize extends Newtype[Long] {
    extension (a: Type) def value: Long = unwrap(a)
  }

  given Config[BatchSize.Type] = Config.long.nested("mailBatchSize").withDefault(1024L).map(BatchSize(_))

  def live(): ZLayer[Scope & Tracing & MailService, Throwable, MailClient] =
    ZLayer.scoped {

      for {
        tracing <- ZIO.service[Tracing]
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

      } yield new MailClientLive(pool).traced(tracing)
    }

  class MailClientLive(pool: ZKeyedPool[Throwable, MailAccount.Id, Gmail]) extends MailClient { self =>
    def loadLabels(accountKey: MailAccount.Id): RIO[Scope, Chunk[MailLabels.LabelInfo]] =
      for {
        service <- pool.get(accountKey)
        response <- ZIO.attempt(service.users().labels().list("me").execute())

        labels = response
          .getLabels()
          .asScala
          .toList
          .map(l =>
            MailLabels.LabelInfo(
              MailLabels.LabelKey(l.getId()),
              MailLabels.Name(l.getName()),
              MailLabels.TotalMessages(l.getMessagesTotal()),
            ),
          )

      } yield Chunk(labels*)

    def fetchMails(
      address: MailAddress,
      token: Option[MailToken],
    ): RIO[Scope, (Chunk[MailData.Id], Option[MailToken])] =
      for {
        service <- pool.get(address.accountKey)
        batchSize <- ZIO.config[BatchSize.Type]

        response <- ZIO.attempt {
          val builder = service
            .users()
            .messages()
            .list("me")
            .setMaxResults(batchSize.value)
            .setLabelIds(java.util.Collections.singletonList(address.label.value))

          val r = token.fold(builder)(l => builder.setPageToken(l.value))

          r.execute()
        }

        messages = response.getMessages().asScala.toList.map(m => MailData.Id(m.getId()))

      } yield (
        Chunk(messages*),
        Option(response.getNextPageToken()).map(MailToken(_)),
      )

    def loadMessage(accountKey: MailAccount.Id, id: MailData.Id): RIO[Scope, Option[MailData]] =
      for {
        service <- pool.get(accountKey)

        response <- ZIO.attempt {
          service
            .users()
            .messages()
            .get("me", id.value)
            .setFormat("raw")
            .execute()
        }

        timestamp <- Timestamp.now

        mail = (
          Option(response.getId()),
          Option(response.getRaw()),
          Option(response.getInternalDate()),
        ).mapN { case (id, body, internalDate) =>
          MailData(
            id = MailData.Id(id),
            body = MailData.Body(body),
            accountKey = accountKey,
            internalDate = InternalDate(Timestamp(internalDate)),
            timestamp = timestamp,
          )
        }

      } yield mail

    def traced(tracing: Tracing): MailClient =
      new MailClient {
        def loadLabels(accountKey: MailAccount.Id): RIO[Scope, Chunk[MailLabels.LabelInfo]] =
          self.loadLabels(accountKey) @@ tracing.aspects.span(
            "MailClient.loadLabels",
            attributes = Attributes(Attribute.long("accountId", accountKey.asKey.value)),
          )
        def fetchMails(
          address: MailAddress,
          token: Option[MailToken],
        ): RIO[Scope, (Chunk[MailData.Id], Option[MailToken])] =
          self.fetchMails(address, token) @@ tracing.aspects.span(
            "MailClient.fetchMails",
            attributes = Attributes(
              Attribute.long("accountId", address.accountKey.asKey.value),
              Attribute.string("label", address.label.value),
            ),
          )
        def loadMessage(accountKey: MailAccount.Id, id: MailData.Id): RIO[Scope, Option[MailData]] =
          self.loadMessage(accountKey, id) @@ tracing.aspects.span(
            "MailClient.loadMessage",
            attributes = Attributes(Attribute.long("accountId", accountKey.asKey.value)),
          )
      }
  }
}

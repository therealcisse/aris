package com.youtoo
package mail
package integration

import zio.*

import com.youtoo.mail.model.*

import zio.prelude.*

import scala.jdk.CollectionConverters.*

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*

trait MailClient {
  def loadLabels(accountKey: MailAccount.Id): RIO[Scope, Chunk[MailLabels.LabelInfo]]
  def fetchMails(
    accountKey: MailAccount.Id,
    token: Option[MailToken],
    labels: Set[MailLabels.LabelKey],
  ): RIO[Scope, Option[(NonEmptyList[MailData.Id], MailToken)]]
  def loadMessage(accountKey: MailAccount.Id, id: MailData.Id): RIO[Scope, Option[MailData]]
}

object MailClient {
  object BatchSize extends Newtype[Long] {
    extension (a: Type) def value: Long = unwrap(a)
  }

  given Config[BatchSize.Type] = Config.long.nested("mailBatchSize").withDefault(1024L).map(BatchSize(_))

  def live(): ZLayer[Scope & Tracing & GmailPool, Throwable, MailClient] =
    ZLayer.scoped {

      for {
        tracing <- ZIO.service[Tracing]
        pool <- ZIO.service[GmailPool]

      } yield new MailClientLive(pool).traced(tracing)
    }

  class MailClientLive(pool: GmailPool) extends MailClient { self =>
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
              TotalMessages(l.getMessagesTotal()),
            ),
          )

      } yield Chunk(labels*)

    def fetchMails(
      accountKey: MailAccount.Id,
      token: Option[MailToken],
      labels: Set[MailLabels.LabelKey],
    ): RIO[Scope, Option[(NonEmptyList[MailData.Id], MailToken)]] =
      for {
        service <- pool.get(accountKey)
        batchSize <- ZIO.config[BatchSize.Type]

        response <- ZIO.attempt {
          val builder = service
            .users()
            .messages()
            .list("me")
            .setMaxResults(batchSize.value)
            .setLabelIds(labels.map(_.value).toList.asJava)

          val r = token.fold(builder)(l => builder.setPageToken(l.value))

          r.execute()
        }

        result = Option(response.getNextPageToken()) flatMap { token =>
          val messages = response.getMessages().asScala.toList.map(m => MailData.Id(m.getId()))

          NonEmptyList.fromIterableOption(messages).map { nel =>
            (nel, MailToken(token))

          }
        }

      } yield result

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
          accountKey: MailAccount.Id,
          token: Option[MailToken],
          labels: Set[MailLabels.LabelKey],
        ): RIO[Scope, Option[(NonEmptyList[MailData.Id], MailToken)]] =
          self.fetchMails(accountKey, token, labels) @@ tracing.aspects.span(
            "MailClient.fetchMails",
            attributes = Attributes(
              Attribute.long("accountId", accountKey.asKey.value),
              Attribute.stringList("labels", labels.map(_.value).toList),
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

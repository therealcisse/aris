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
    labels: Option[NonEmptySet[MailLabels.LabelKey]],
  ): RIO[Tracing, Option[(NonEmptyList[MailData.Id], MailToken)]]
  def loadMessage(accountKey: MailAccount.Id, id: MailData.Id): Task[Option[MailData]]
}

object MailClient {
  object BatchSize extends Newtype[Long] {
    extension (a: Type) def value: Long = unwrap(a)
  }

  given Config[BatchSize.Type] = Config.long.nested("mailBatchSize").withDefault(128L).map(BatchSize(_))

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

        labels = Option(response)
          .flatMap(r => Option(r.getLabels()))
          .map(_.asScala.toList.map { l =>
            MailLabels.LabelInfo(
              MailLabels.LabelKey(l.getId()),
              MailLabels.Name(l.getName()),
              TotalMessages(l.getMessagesTotal()),
            )
          })
          .getOrElse(Nil)

      } yield Chunk(labels*)

    def fetchMails(
      accountKey: MailAccount.Id,
      token: Option[MailToken],
      labels: Option[NonEmptySet[MailLabels.LabelKey]],
    ): RIO[Tracing, Option[(NonEmptyList[MailData.Id], MailToken)]] =
      ZIO.scoped {

        for {
          service <- pool.get(accountKey)
          batchSize <- ZIO.config[BatchSize.Type]

          _ <- Log.debug(s"Fetching mails from : $token")

          response <- ZIO.attempt {

            val builder = service
              .users()
              .messages()
              .list("me")
              .setMaxResults(batchSize.value)

            val withToken = token.fold(builder)(l => builder.setPageToken(l.value))
            val withLabels = labels.fold(withToken)(l => withToken.setLabelIds(l.map(_.value).toList.asJava))

            withLabels.execute()
          }

          result =
            if response == null then None
            else
              Option(response.getNextPageToken()) flatMap { token =>
                val messages =
                  Option(response.getMessages()).fold(Nil)(_.asScala.toList.map(m => MailData.Id(m.getId())))

                NonEmptyList.fromIterableOption(messages).map { nel =>
                  (nel, MailToken(token))

                }
              }

          _ = result match {
            case None => Log.debug(s"Fetched 0 mails from : $token")
            case Some((nel, nextToken)) =>
              Log.debug(s"Fetched ${nel.size} mails from : $token, nextToken=$nextToken")
          }
        } yield result
      }

    def loadMessage(accountKey: MailAccount.Id, id: MailData.Id): Task[Option[MailData]] =
      ZIO.scoped {

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

          timestamp <- Timestamp.gen

          mail =
            if response == null then None
            else
              (
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
      }

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
          labels: Option[NonEmptySet[MailLabels.LabelKey]],
        ): RIO[Tracing, Option[(NonEmptyList[MailData.Id], MailToken)]] =
          self.fetchMails(accountKey, token, labels) @@ tracing.aspects.span(
            "MailClient.fetchMails",
            attributes = Attributes(Attribute.long("accountId", accountKey.asKey.value)),
          )
        def loadMessage(accountKey: MailAccount.Id, id: MailData.Id): Task[Option[MailData]] =
          self.loadMessage(accountKey, id) @@ tracing.aspects.span(
            "MailClient.loadMessage",
            attributes = Attributes(Attribute.long("accountId", accountKey.asKey.value)),
          )
      }
  }
}

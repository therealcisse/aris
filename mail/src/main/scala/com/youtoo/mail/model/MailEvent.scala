package com.youtoo
package mail
package model

import cats.implicits.*

import zio.*
import zio.prelude.*

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.*
import com.youtoo.job.model.*

enum MailEvent {
  case SyncStarted(labels: MailLabels, timestamp: Timestamp, jobId: Job.Id)
  case MailSynced(
    timestamp: Timestamp,
    mailKeys: NonEmptyList[MailData.Id],
    token: MailToken,
    jobId: Job.Id,
  )
  case SyncCompleted(timestamp: Timestamp, jobId: Job.Id)
}

object MailEvent {
  import zio.schema.*

  given Schema[MailEvent] = DeriveSchema.gen

  val discriminator = Discriminator("Mail")

  given MetaInfo[MailEvent] {

    extension (self: MailEvent)
      def namespace: Namespace = self match {
        case _: MailEvent.SyncStarted => Namespace(0)
        case _: MailEvent.MailSynced => Namespace(1)
        case _: MailEvent.SyncCompleted => Namespace(2)

      }

    extension (self: MailEvent) def hierarchy: Option[Hierarchy] = None
    extension (self: MailEvent) def props: Chunk[EventProperty] = Chunk.empty
    extension (self: MailEvent) def reference: Option[Reference] = None
  }

  class LoadCursor() extends EventHandler[MailEvent, Option[Cursor]] {
    def applyEvents(events: NonEmptyList[Change[MailEvent]]): Option[Cursor] =
      events match {
        case NonEmptyList.Single(ch) =>
          ch.payload match {
            case MailEvent.MailSynced(timestamp, mailKeys, token, _) =>
              Cursor(
                timestamp,
                token,
                total = TotalMessages(mailKeys.size),
                isSyncing = true,
              ).some

            case _ => None
          }

        case NonEmptyList.Cons(ch, ls) =>
          ch.payload match {
            case MailEvent.MailSynced(timestamp, mailKeys, token, _) =>
              applyEvents(
                zero = Cursor(
                  timestamp,
                  token,
                  total = TotalMessages(mailKeys.size),
                  isSyncing = true,
                ).some,
                ls,
              )

            case _ =>
              applyEvents(
                zero = None,
                ls,
              )


          }

      }

    def applyEvents(zero: Option[Cursor], events: NonEmptyList[Change[MailEvent]]): Option[Cursor] =
      events.foldLeft(zero) { (state, e) =>

        e.payload match {
          case MailEvent.MailSynced(timestamp, mailKeys, token, _) =>
              Cursor(
                timestamp,
                token,
                total = state match {
                  case None => TotalMessages(mailKeys.size)
                  case Some(s) => s.total + mailKeys.size
                },
                isSyncing = true,
              ).some

          case _ =>
            state.map(_.copy(isSyncing = false))
        }
      }

  }


}

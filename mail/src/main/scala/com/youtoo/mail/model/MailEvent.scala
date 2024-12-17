package com.youtoo
package mail
package model

import zio.*

import zio.prelude.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.job.model.*

enum MailEvent {
  case SyncStarted(labels: MailLabels, timestamp: Timestamp, jobId: Job.Id)
  case MailSynced(
    timestamp: Timestamp,
    mailKeys: List[MailData.Id],
    token: Option[MailToken],
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

  class LoadMail(accountKey: MailAccount.Id) extends EventHandler[MailEvent, Mail] {
    def applyEvents(events: NonEmptyList[Change[MailEvent]]): Mail =
      events match {
        case NonEmptyList.Single(ch) =>
          ch.payload match {
            case MailEvent.SyncStarted(labels, timestamp, jobId) =>
              Mail(accountKey = accountKey, state = Mail.State())

            case _ => throw IllegalArgumentException("Unexpected event, current state is empty")
          }

        case NonEmptyList.Cons(ch, ls) =>
          ch.payload match {
            case MailEvent.SyncStarted(labels, timestamp, jobId) =>
              applyEvents(zero = Mail(accountKey = accountKey, state = Mail.State()), ls)

            case _ => throw IllegalArgumentException("Unexpected event, current state is empty")
          }

      }

    def applyEvents(zero: Mail, events: NonEmptyList[Change[MailEvent]]): Mail =
      events.foldLeft(zero) { (state, e) =>

        e.payload match {
          case MailEvent.SyncStarted(labels, timestamp, jobId) =>
            state.copy(accountKey = accountKey, state = Mail.State())

          case MailEvent.MailSynced(timestamp, mailKeys, token, jobId) =>
            state

          case MailEvent.SyncCompleted(timestamp, jobId) =>
            state
        }
      }

  }

}

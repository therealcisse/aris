package com.youtoo
package mail
package model

import zio.*

import com.youtoo.cqrs.*
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

}

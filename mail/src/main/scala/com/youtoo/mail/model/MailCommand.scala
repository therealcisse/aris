package com.youtoo
package mail
package model

import zio.prelude.*

import com.youtoo.cqrs.*
import com.youtoo.job.model.*

enum MailCommand {
  case StartSync(labels: MailLabels, timestamp: Timestamp, jobId: Job.Id)
  case GrantAuthorization(token: TokenInfo, timestamp: Timestamp)
  case RevokeAuthorization(timestamp: Timestamp)
  case RecordSync(timestamp: Timestamp, keys: NonEmptyList[MailData.Id], token: MailToken, jobId: Job.Id)
  case CompleteSync(timestamp: Timestamp, jobId: Job.Id)
}

object MailCommand {
  import zio.schema.*

  given Schema[MailCommand] = DeriveSchema.gen

  given CmdHandler[MailCommand, MailEvent] {

    def applyCmd(cmd: MailCommand): NonEmptyList[MailEvent] =
      cmd match {
        case MailCommand.StartSync(labels, timestamp, jobId) =>
          NonEmptyList(MailEvent.SyncStarted(labels = labels, timestamp = timestamp, jobId = jobId))

        case MailCommand.GrantAuthorization(token, timestamp) =>
          NonEmptyList(MailEvent.AuthorizationGranted(token = token, timestamp = timestamp))

        case MailCommand.RevokeAuthorization(timestamp) =>
          NonEmptyList(MailEvent.AuthorizationRevoked(timestamp = timestamp))

        case MailCommand.RecordSync(timestamp, keys, token, jobId) =>
          NonEmptyList(MailEvent.MailSynced(timestamp = timestamp, mailKeys = keys, token = token, jobId = jobId))

        case MailCommand.CompleteSync(timestamp, jobId) =>
          NonEmptyList(MailEvent.SyncCompleted(timestamp = timestamp, jobId = jobId))

      }
  }

}


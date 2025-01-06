package com.youtoo
package mail
package service

import com.youtoo.mail.model.*
import com.youtoo.job.model.*
import com.youtoo.sink.model.*

import zio.prelude.*
import zio.mock.*
import zio.*

object MailServiceMock extends Mock[MailService] {

  object StartSync extends Effect[(MailAccount.Id, MailLabels, Timestamp, Job.Id), Throwable, Unit]
  object GrantAuthorization extends Effect[(MailAccount.Id, TokenInfo, Timestamp), Throwable, Unit]
  object RevokeAuthorization extends Effect[(MailAccount.Id, Timestamp), Throwable, Unit]

  object RecordSynced
      extends Effect[(MailAccount.Id, Timestamp, NonEmptyList[MailData.Id], MailToken, Job.Id), Throwable, Unit]
  object CompleteSync extends Effect[(MailAccount.Id, Timestamp, Job.Id), Throwable, Unit]

  object SetAutoSync extends Effect[(MailAccount.Id, SyncConfig.CronExpression), Throwable, Unit]
  object DisableAutoSync extends Effect[MailAccount.Id, Throwable, Unit]
  object SetAuthConfig extends Effect[(MailAccount.Id, AuthConfig), Throwable, Unit]
  object LinkSink extends Effect[(MailAccount.Id, SinkDefinition.Id), Throwable, Unit]
  object UnlinkSink extends Effect[(MailAccount.Id, SinkDefinition.Id), Throwable, Unit]

  object LoadAccounts extends Effect[Unit, Throwable, Chunk[MailAccount]]
  object LoadAccount extends Effect[MailAccount.Id, Throwable, Option[MailAccount]]
  object SaveAccount extends Effect[(MailAccount.Id, MailAccount.Information), Throwable, Long]
  object LoadMail extends Effect[MailData.Id, Throwable, Option[MailData]]
  object LoadMails extends Effect[(Option[Long], Long), Throwable, Chunk[MailData.Id]]
  object SaveMail extends Effect[MailData, Throwable, Long]
  object SaveMails extends Effect[NonEmptyList[MailData], Throwable, Long]
  object LoadState extends Effect[MailAccount.Id, Throwable, Option[Mail]]
  object LoadDownloadState extends Effect[MailAccount.Id, Throwable, Option[Download]]

  val compose: URLayer[Proxy, MailService] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new MailService {
        def startSync(accountKey: MailAccount.Id, labels: MailLabels, timestamp: Timestamp, jobId: Job.Id): Task[Unit] =
          proxy(StartSync, (accountKey, labels, timestamp, jobId))

        def grantAuthorization(accountKey: MailAccount.Id, token: TokenInfo, timestamp: Timestamp): Task[Unit] =
          proxy(GrantAuthorization, (accountKey, token, timestamp))

        def revokeAuthorization(accountKey: MailAccount.Id, timestamp: Timestamp): Task[Unit] =
          proxy(RevokeAuthorization, accountKey, timestamp)

        def recordSynced(
          accountKey: MailAccount.Id,
          timestamp: Timestamp,
          mailKeys: NonEmptyList[MailData.Id],
          token: MailToken,
          jobId: Job.Id,
        ): Task[Unit] =
          proxy(RecordSynced, (accountKey, timestamp, mailKeys, token, jobId))

        def completeSync(accountKey: MailAccount.Id, timestamp: Timestamp, jobId: Job.Id): Task[Unit] =
          proxy(CompleteSync, (accountKey, timestamp, jobId))

        def setAutoSync(accountId: MailAccount.Id, schedule: SyncConfig.CronExpression): Task[Unit] =
          proxy(SetAutoSync, (accountId, schedule))

        def disableAutoSync(accountId: MailAccount.Id): Task[Unit] =
          proxy(DisableAutoSync, accountId)

        def setAuthConfig(accountId: MailAccount.Id, config: AuthConfig): Task[Unit] =
          proxy(SetAuthConfig, (accountId, config))

        def linkSink(accountId: MailAccount.Id, sinkId: SinkDefinition.Id): Task[Unit] =
          proxy(LinkSink, (accountId, sinkId))

        def unlinkSink(accountId: MailAccount.Id, sinkId: SinkDefinition.Id): Task[Unit] =
          proxy(UnlinkSink, (accountId, sinkId))

        def loadAccounts(): Task[Chunk[MailAccount]] =
          proxy(LoadAccounts)

        def loadAccount(key: MailAccount.Id): Task[Option[MailAccount]] =
          proxy(LoadAccount, key)

        def save(id: MailAccount.Id, info: MailAccount.Information): Task[Long] =
          proxy(SaveAccount, (id, info))

        def loadMail(id: MailData.Id): Task[Option[MailData]] =
          proxy(LoadMail, id)

        def loadState(accountKey: MailAccount.Id): Task[Option[Mail]] =
          proxy(LoadState, accountKey)

        def loadDownloadState(accountKey: MailAccount.Id): Task[Option[Download]] =
          proxy(LoadDownloadState, accountKey)

        def loadMails(offset: Option[Long], limit: Long): Task[Chunk[MailData.Id]] =
          proxy(LoadMails, (offset, limit))

        def save(data: MailData): Task[Long] =
          proxy(SaveMail, data)

        def saveMails(data: NonEmptyList[MailData]): Task[Long] =
          proxy(SaveMails, data)

      }
    }
}

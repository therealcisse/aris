package com.youtoo
package mail
package service

import com.youtoo.mail.model.*
import com.youtoo.job.model.*

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

  object LoadAccounts extends Effect[Unit, Throwable, Chunk[MailAccount]]
  object LoadAccount extends Effect[MailAccount.Id, Throwable, Option[MailAccount]]
  object SaveAccount extends Effect[MailAccount, Throwable, Long]
  object LoadMail extends Effect[MailData.Id, Throwable, Option[MailData]]
  object LoadMails extends Effect[(Option[Long], Long), Throwable, Chunk[MailData.Id]]
  object SaveMail extends Effect[MailData, Throwable, Long]
  object LoadState extends Effect[MailAccount.Id, Throwable, Option[Mail]]
  object UpdateMailSettings extends Effect[(MailAccount.Id, MailSettings), Throwable, Long]

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

        def loadAccounts(): Task[Chunk[MailAccount]] =
          proxy(LoadAccounts)

        def loadAccount(key: MailAccount.Id): Task[Option[MailAccount]] =
          proxy(LoadAccount, key)

        def save(account: MailAccount): Task[Long] =
          proxy(SaveAccount, account)

        def loadMail(id: MailData.Id): Task[Option[MailData]] =
          proxy(LoadMail, id)

        def loadState(accountKey: MailAccount.Id): Task[Option[Mail]] =
          proxy(LoadState, accountKey)

        def loadMails(offset: Option[Long], limit: Long): Task[Chunk[MailData.Id]] =
          proxy(LoadMails, (offset, limit))

        def save(data: MailData): Task[Long] =
          proxy(SaveMail, data)

        def updateMailSettings(id: MailAccount.Id, settings: MailSettings): Task[Long] =
          proxy(UpdateMailSettings, (id, settings))
      }
    }
}

package com.youtoo
package mail
package repository

import zio.*
import zio.mock.*
import zio.jdbc.*

import com.youtoo.mail.model.*

object MailRepositoryMock extends Mock[MailRepository] {

  object LoadMails extends Effect[(Option[Long], Long), Throwable, Chunk[MailData.Id]]
  object LoadAccounts extends Effect[Unit, Throwable, Chunk[MailAccount]]
  object LoadAccount extends Effect[MailAccount.Id, Throwable, Option[MailAccount]]
  object SaveAccount extends Effect[MailAccount, Throwable, Long]
  object LoadMail extends Effect[MailData.Id, Throwable, Option[MailData]]
  object SaveMail extends Effect[MailData, Throwable, Long]
  object UpdateMailSettings extends Effect[(MailAccount.Id, MailSettings), Throwable, Long]

  val compose: URLayer[Proxy, MailRepository] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new MailRepository {
        def loadMails(offset: Option[Long], limit: Long): RIO[ZConnection, Chunk[MailData.Id]] =
          proxy(LoadMails, (offset, limit))

        def loadAccounts(): RIO[ZConnection, Chunk[MailAccount]] =
          proxy(LoadAccounts)

        def loadAccount(key: MailAccount.Id): RIO[ZConnection, Option[MailAccount]] =
          proxy(LoadAccount, key)

        def save(account: MailAccount): RIO[ZConnection, Long] =
          proxy(SaveAccount, account)

        def loadMail(id: MailData.Id): RIO[ZConnection, Option[MailData]] =
          proxy(LoadMail, id)

        def save(data: MailData): RIO[ZConnection, Long] =
          proxy(SaveMail, data)

        def updateMailSettings(id: MailAccount.Id, settings: MailSettings): RIO[ZConnection, Long] =
          proxy(UpdateMailSettings, (id, settings))
      }
    }
}

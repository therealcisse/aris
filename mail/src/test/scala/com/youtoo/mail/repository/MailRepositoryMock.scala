package com.youtoo
package mail
package repository

import zio.*
import zio.mock.*
import zio.jdbc.*
import zio.prelude.*

import com.youtoo.mail.model.*

object MailRepositoryMock extends Mock[MailRepository] {

  object LoadMails extends Effect[(Option[Long], Long), Throwable, Chunk[MailData.Id]]
  object LoadAccounts extends Effect[Unit, Throwable, Chunk[MailAccount.Id]]
  object LoadAccount extends Effect[MailAccount.Id, Throwable, Option[MailAccount.Information]]
  object SaveAccount extends Effect[(MailAccount.Id, MailAccount.Information), Throwable, Long]
  object LoadMail extends Effect[MailData.Id, Throwable, Option[MailData]]
  object SaveMail extends Effect[MailData, Throwable, Long]
  object SaveMails extends Effect[NonEmptyList[MailData], Throwable, Long]

  val compose: URLayer[Proxy, MailRepository] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new MailRepository {
        def loadMails(offset: Option[Long], limit: Long): RIO[ZConnection, Chunk[MailData.Id]] =
          proxy(LoadMails, (offset, limit))

        def loadAccounts(): RIO[ZConnection, Chunk[MailAccount.Id]] =
          proxy(LoadAccounts)

        def loadAccount(key: MailAccount.Id): RIO[ZConnection, Option[MailAccount.Information]] =
          proxy(LoadAccount, key)

        def save(id: MailAccount.Id, info: MailAccount.Information): RIO[ZConnection, Long] =
          proxy(SaveAccount, (id, info))

        def loadMail(id: MailData.Id): RIO[ZConnection, Option[MailData]] =
          proxy(LoadMail, id)

        def save(data: MailData): RIO[ZConnection, Long] =
          proxy(SaveMail, data)

        def saveMails(data: NonEmptyList[MailData]): RIO[ZConnection, Long] =
          proxy(SaveMails, data)

      }
    }
}

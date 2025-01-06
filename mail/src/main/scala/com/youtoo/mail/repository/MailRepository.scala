package com.youtoo
package mail
package repository

import zio.*
import zio.prelude.*

import com.youtoo.job.model.*
import com.youtoo.mail.model.*
import com.youtoo.cqrs.*
import com.youtoo.cqrs.service.postgres.*

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*

import zio.jdbc.*

trait MailRepository {
  def loadMails(offset: Option[Long], limit: Long): RIO[ZConnection, Chunk[MailData.Id]]
  def loadAccounts(): RIO[ZConnection, Chunk[MailAccount.Id]]
  def loadAccount(key: MailAccount.Id): RIO[ZConnection, Option[MailAccount.Information]]
  def save(id: MailAccount.Id, info: MailAccount.Information): RIO[ZConnection, Long]
  def loadMail(id: MailData.Id): RIO[ZConnection, Option[MailData]]
  def save(data: MailData): RIO[ZConnection, Long]
  def saveMails(data: NonEmptyList[MailData]): RIO[ZConnection, Long]
}

object MailRepository {
  inline def loadMails(offset: Option[Long], limit: Long): RIO[MailRepository & ZConnection, Chunk[MailData.Id]] =
    ZIO.serviceWithZIO[MailRepository](_.loadMails(offset, limit))

  inline def loadAccounts(): RIO[MailRepository & ZConnection, Chunk[MailAccount.Id]] =
    ZIO.serviceWithZIO[MailRepository](_.loadAccounts())

  inline def loadAccount(key: MailAccount.Id): RIO[MailRepository & ZConnection, Option[MailAccount.Information]] =
    ZIO.serviceWithZIO[MailRepository](_.loadAccount(key))

  inline def save(id: MailAccount.Id, info: MailAccount.Information): RIO[MailRepository & ZConnection, Long] =
    ZIO.serviceWithZIO[MailRepository](_.save(id, info))

  inline def loadMail(id: MailData.Id): RIO[MailRepository & ZConnection, Option[MailData]] =
    ZIO.serviceWithZIO[MailRepository](_.loadMail(id))

  inline def save(data: MailData): RIO[MailRepository & ZConnection, Long] =
    ZIO.serviceWithZIO[MailRepository](_.save(data))

  inline def saveMails(data: NonEmptyList[MailData]): RIO[MailRepository & ZConnection, Long] =
    ZIO.serviceWithZIO[MailRepository](_.saveMails(data))

  def live(): ZLayer[Tracing, Throwable, MailRepository] =
    ZLayer.fromFunction(tracing => new MailRepositoryLive().traced(tracing))

  class MailRepositoryLive() extends MailRepository { self =>
    def loadMails(offset: Option[Long], limit: Long): RIO[ZConnection, Chunk[MailData.Id]] =
      Queries.LOAD_MAILS(offset, limit).selectAll

    def loadAccounts(): RIO[ZConnection, Chunk[MailAccount.Id]] =
      Queries.LOAD_ACCOUNTS().selectAll

    def loadAccount(key: MailAccount.Id): RIO[ZConnection, Option[MailAccount.Information]] =
      Queries.LOAD_ACCOUNT(key).selectOne

    def save(id: MailAccount.Id, info: MailAccount.Information): RIO[ZConnection, Long] =
      Queries.SAVE_ACCOUNT(id, info).insert

    def loadMail(id: MailData.Id): RIO[ZConnection, Option[MailData]] =
      Queries.LOAD_MAIL(id).selectOne

    def save(data: MailData): RIO[ZConnection, Long] =
      Queries.SAVE_MAIL(data).insert

    def saveMails(data: NonEmptyList[MailData]): RIO[ZConnection, Long] =
      Queries.SAVE_MAILS(data).insert

    def traced(tracing: Tracing): MailRepository =
      new MailRepository {
        def loadMails(offset: Option[Long], limit: Long): RIO[ZConnection, Chunk[MailData.Id]] =
          self.loadMails(offset, limit) @@ tracing.aspects.span("MailRepository.loadMails")
        def loadAccounts(): RIO[ZConnection, Chunk[MailAccount.Id]] =
          self.loadAccounts() @@ tracing.aspects.span("MailRepository.loadAccounts")
        def loadAccount(key: MailAccount.Id): RIO[ZConnection, Option[MailAccount.Information]] =
          self.loadAccount(key) @@ tracing.aspects.span(
            "MailRepository.loadAccount",
            attributes = Attributes(Attribute.long("accountId", key.asKey.value)),
          )
        def save(id: MailAccount.Id, info: MailAccount.Information): RIO[ZConnection, Long] =
          self.save(id, info) @@ tracing.aspects.span(
            "MailRepository.save",
            attributes = Attributes(Attribute.long("accountId", id.asKey.value)),
          )
        def loadMail(id: MailData.Id): RIO[ZConnection, Option[MailData]] =
          self.loadMail(id) @@ tracing.aspects.span(
            "MailRepository.loadMail",
            attributes = Attributes(Attribute.string("mailId", id.value)),
          )
        def save(data: MailData): RIO[ZConnection, Long] =
          self.save(data) @@ tracing.aspects.span(
            "MailRepository.save",
            attributes = Attributes(Attribute.string("mailId", data.id.value)),
          )
        def saveMails(data: NonEmptyList[MailData]): RIO[ZConnection, Long] =
          self.saveMails(data) @@ tracing.aspects.span(
            "MailRepository.saveMails",
            attributes = Attributes(Attribute.long("mails", data.size)),
          )

      }
  }

  object Queries extends JdbcCodecs {
    import zio.jdbc.*

    given mailAccountIdSetter: SqlFragment.Setter[MailAccount.Id] = SqlFragment.Setter[Key].contramap(_.asKey)
    given mailDataIdSetter: SqlFragment.Setter[MailData.Id] = SqlFragment.Setter[String].contramap(_.value)
    given emailSetter: SqlFragment.Setter[MailAccount.Name] = SqlFragment.Setter[String].contramap(_.value)
    given nameSetter: SqlFragment.Setter[MailAccount.Email] = SqlFragment.Setter[String].contramap(_.value)
    given jobIdSetter: SqlFragment.Setter[Job.Id] = SqlFragment.Setter[Key].contramap(_.asKey)
    given bodySetter: SqlFragment.Setter[MailData.Body] = SqlFragment.Setter[String].contramap(_.value)
    given internalDateSetter: SqlFragment.Setter[InternalDate] = SqlFragment.Setter[Timestamp].contramap(_.value)
    given accountTypeSetter: SqlFragment.Setter[AccountType] = SqlFragment.Setter[String].contramap(_.name)

    given mailDataIdDecoder: JdbcDecoder[MailData.Id] = JdbcDecoder[String].map(MailData.Id.apply)
    given bodyDecoder: JdbcDecoder[MailData.Body] = JdbcDecoder[String].map(MailData.Body.apply)
    given internalDateDecoder: JdbcDecoder[InternalDate] = JdbcDecoder[Timestamp].map(InternalDate.apply)

    given mailAccountIdDecoder: JdbcDecoder[MailAccount.Id] = JdbcDecoder[Long].map(MailAccount.Id.apply)
    given nameDecoder: JdbcDecoder[MailAccount.Name] = JdbcDecoder[String].map(MailAccount.Name.apply)
    given emailDecoder: JdbcDecoder[MailAccount.Email] = JdbcDecoder[String].map(MailAccount.Email.apply)
    given accountTypeDecoder: JdbcDecoder[AccountType] = JdbcDecoder[String].map(AccountType.valueOf)

    given jobIdDecoder: JdbcDecoder[Job.Id] = JdbcDecoder[Key].map(Job.Id.apply)

    def LOAD_MAILS(offset: Option[Long], limit: Long): Query[MailData.Id] =
      val offsetQuery = offset.map(o => sql" OFFSET $offset ").getOrElse(SqlFragment.empty)
      val limitQuery = sql" LIMIT $limit "

      (sql"""
        SELECT id
        FROM mail_data
        """ ++ sql" ORDER BY id DESC" ++ offsetQuery ++ limitQuery).query[MailData.Id]

    def LOAD_ACCOUNTS(): Query[MailAccount.Id] =
      (sql"""
        SELECT key
        FROM mail_account
        """)
        .query[
          MailAccount.Id,
        ]

    def LOAD_ACCOUNT(key: MailAccount.Id): Query[MailAccount.Information] =
      sql"""
    SELECT type, name, email, timestamp
    FROM mail_account
    WHERE key = $key
    """.query[
        (
          AccountType,
          MailAccount.Name,
          MailAccount.Email,
          Timestamp,
        ),
      ].map(MailAccount.Information.apply)

    def SAVE_ACCOUNT(id: MailAccount.Id, info: MailAccount.Information): SqlFragment =
      sql"""
    INSERT INTO mail_account (key, type, email, name, timestamp)
    VALUES (
      ${id},
      ${info.accountType},
      ${info.email},
      ${info.name},
      ${info.timestamp}
    )
    """

    def LOAD_MAIL(id: MailData.Id): Query[MailData] =
      sql"""
    SELECT id, raw_body, account_id, internal_date, timestamp
    FROM mail_data
    WHERE id = $id
    """.query[
        (
          MailData.Id,
          MailData.Body,
          MailAccount.Id,
          InternalDate,
          Timestamp,
        ),
      ].map(MailData.apply)

    def SAVE_MAIL(mail: MailData): SqlFragment =
      sql"""
    INSERT INTO mail_data (id, raw_body, account_id, internal_date, timestamp)
    VALUES (
      ${mail.id},
      ${mail.body},
      ${mail.accountKey},
      ${mail.internalDate},
      ${mail.timestamp}
    )
    """

    def SAVE_MAILS(data: NonEmptyList[MailData]): SqlFragment =
      SqlFragment
        .insertInto("mail_data")(
          "id",
          "raw_body",
          "account_id",
          "internal_date",
          "timestamp",
        )
        .values(
          data
            .map(m =>
              (
                m.id,
                m.body,
                m.accountKey,
                m.internalDate,
                m.timestamp,
              ),
            )
            .toSeq,
        )

  }

}

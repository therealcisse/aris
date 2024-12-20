package com.youtoo
package mail
package repository

import zio.*

import com.youtoo.cqrs.Codecs.given

import com.youtoo.job.model.*
import com.youtoo.mail.model.*
import com.youtoo.cqrs.*
import com.youtoo.cqrs.service.postgres.*

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*

import zio.schema.codec.*

import zio.jdbc.*

trait MailRepository {
  def loadMails(options: FetchOptions): RIO[ZConnection, Chunk[MailData.Id]]
  def loadAccounts(options: FetchOptions): RIO[ZConnection, Chunk[MailAccount]]
  def loadAccount(key: MailAccount.Id): RIO[ZConnection, Option[MailAccount]]
  def save(account: MailAccount): RIO[ZConnection, Long]
  def loadMail(id: MailData.Id): RIO[ZConnection, Option[MailData]]
  def save(data: MailData): RIO[ZConnection, Long]
}

object MailRepository {
  inline def loadMails(options: FetchOptions): RIO[MailRepository & ZConnection, Chunk[MailData.Id]] =
    ZIO.serviceWithZIO[MailRepository](_.loadMails(options))

  inline def loadAccounts(options: FetchOptions): RIO[MailRepository & ZConnection, Chunk[MailAccount]] =
    ZIO.serviceWithZIO[MailRepository](_.loadAccounts(options))

  inline def loadAccount(key: MailAccount.Id): RIO[MailRepository & ZConnection, Option[MailAccount]] =
    ZIO.serviceWithZIO[MailRepository](_.loadAccount(key))

  inline def save(account: MailAccount): RIO[MailRepository & ZConnection, Long] =
    ZIO.serviceWithZIO[MailRepository](_.save(account))

  inline def loadMail(id: MailData.Id): RIO[MailRepository & ZConnection, Option[MailData]] =
    ZIO.serviceWithZIO[MailRepository](_.loadMail(id))

  inline def save(data: MailData): RIO[MailRepository & ZConnection, Long] =
    ZIO.serviceWithZIO[MailRepository](_.save(data))

  def live(): ZLayer[Tracing, Throwable, MailRepository] =
    ZLayer.fromFunction(tracing => new MailRepositoryLive().traced(tracing))

  class MailRepositoryLive() extends MailRepository { self =>
    def loadMails(options: FetchOptions): RIO[ZConnection, Chunk[MailData.Id]] =
      Queries.LOAD_MAILS(options).selectAll

    def loadAccounts(options: FetchOptions): RIO[ZConnection, Chunk[MailAccount]] =
      Queries.LOAD_ACCOUNTS(options).selectAll

    def loadAccount(key: MailAccount.Id): RIO[ZConnection, Option[MailAccount]] =
      Queries.LOAD_ACCOUNT(key).selectOne

    def save(account: MailAccount): RIO[ZConnection, Long] =
      Queries.SAVE_ACCOUNT(account).insert

    def loadMail(id: MailData.Id): RIO[ZConnection, Option[MailData]] =
      Queries.LOAD_MAIL(id).selectOne

    def save(data: MailData): RIO[ZConnection, Long] =
      Queries.SAVE_MAIL(data).insert

    def traced(tracing: Tracing): MailRepository =
      new MailRepository {
        def loadMails(options: FetchOptions): RIO[ZConnection, Chunk[MailData.Id]] =
          self.loadMails(options) @@ tracing.aspects.span("MailRepository.loadMails")
        def loadAccounts(options: FetchOptions): RIO[ZConnection, Chunk[MailAccount]] =
          self.loadAccounts(options) @@ tracing.aspects.span("MailRepository.loadAccounts")
        def loadAccount(key: MailAccount.Id): RIO[ZConnection, Option[MailAccount]] =
          self.loadAccount(key) @@ tracing.aspects.span(
            "MailRepository.loadAccount",
            attributes = Attributes(Attribute.long("accountId", key.asKey.value)),
          )
        def save(account: MailAccount): RIO[ZConnection, Long] =
          self.save(account) @@ tracing.aspects.span(
            "MailRepository.save",
            attributes = Attributes(Attribute.long("accountId", account.id.asKey.value)),
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
      }
  }

  object Queries extends JdbcCodecs {
    import zio.jdbc.*

    given JdbcDecoder[MailSettings] = byteArrayDecoder[MailSettings]

    given mailAccountIdSetter: SqlFragment.Setter[MailAccount.Id] = SqlFragment.Setter[Key].contramap(_.asKey)
    given mailDataIdSetter: SqlFragment.Setter[MailData.Id] = SqlFragment.Setter[String].contramap(_.value)
    given emailSetter: SqlFragment.Setter[MailAccount.Name] = SqlFragment.Setter[String].contramap(_.value)
    given nameSetter: SqlFragment.Setter[MailAccount.Email] = SqlFragment.Setter[String].contramap(_.value)
    given jobIdSetter: SqlFragment.Setter[Job.Id] = SqlFragment.Setter[Key].contramap(_.asKey)
    given bodySetter: SqlFragment.Setter[MailData.Body] = SqlFragment.Setter[String].contramap(_.value)
    given internalDateSetter: SqlFragment.Setter[InternalDate] = SqlFragment.Setter[Timestamp].contramap(_.value)

    given mailDataIdDecoder: JdbcDecoder[MailData.Id] = JdbcDecoder[String].map(MailData.Id.apply)
    given bodyDecoder: JdbcDecoder[MailData.Body] = JdbcDecoder[String].map(MailData.Body.apply)
    given internalDateDecoder: JdbcDecoder[InternalDate] = JdbcDecoder[Timestamp].map(InternalDate.apply)

    given mailAccountIdDecoder: JdbcDecoder[MailAccount.Id] = JdbcDecoder[Long].map(MailAccount.Id.apply)
    given nameDecoder: JdbcDecoder[MailAccount.Name] = JdbcDecoder[String].map(MailAccount.Name.apply)
    given emailDecoder: JdbcDecoder[MailAccount.Email] = JdbcDecoder[String].map(MailAccount.Email.apply)

    given jobIdDecoder: JdbcDecoder[Job.Id] = JdbcDecoder[Key].map(Job.Id.apply)

    def LOAD_MAILS(options: FetchOptions): Query[MailData.Id] =
      val (offsetQuery, limitQuery) = options.toSql

      (sql"""
        SELECT id
        FROM mail_data
        """ ++ offsetQuery.fold(SqlFragment.empty)(ql => sql" WHERE " ++ ql) ++ sql" ORDER BY id DESC" ++ limitQuery
        .fold(
          SqlFragment.empty,
        )(ql => sql" " ++ ql)).query[MailData.Id]

    def LOAD_ACCOUNTS(options: FetchOptions): Query[MailAccount] =
      val (offsetQuery, limitQuery) = options.toSql

      (sql"""
        SELECT key, name, email, settings, timestamp
        FROM mail_account
        """ ++ offsetQuery.fold(SqlFragment.empty)(ql => sql" WHERE " ++ ql) ++ sql" ORDER BY key DESC" ++ limitQuery
        .fold(
          SqlFragment.empty,
        )(ql => sql" " ++ ql))
        .query[
          (
            MailAccount.Id,
            MailAccount.Name,
            MailAccount.Email,
            MailSettings,
            Timestamp,
          ),
        ]
        .map(MailAccount.apply)

    def LOAD_ACCOUNT(key: MailAccount.Id): Query[MailAccount] =
      sql"""
    SELECT key, name, email, settings, timestamp
    FROM mail_account
    WHERE key = $key
    """.query[
        (
          MailAccount.Id,
          MailAccount.Name,
          MailAccount.Email,
          MailSettings,
          Timestamp,
        ),
      ].map(MailAccount.apply)

    def SAVE_ACCOUNT(account: MailAccount): SqlFragment =
      val payload = java.util.Base64.getEncoder.encodeToString(summon[BinaryCodec[MailAccount]].encode(account).toArray)

      sql"""
    INSERT INTO mail_account (key, email, name, settings, timestamp)
    VALUES (
      ${account.id},
      ${account.email},
      ${account.name},
      decode(${payload}, 'base64'),
      ${account.timestamp}
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

  }

}

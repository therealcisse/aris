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
  def loadMails(offset: Option[Long], limit: Long): RIO[ZConnection, Chunk[MailData.Id]]
  def loadAccounts(): RIO[ZConnection, Chunk[MailAccount]]
  def loadAccount(key: MailAccount.Id): RIO[ZConnection, Option[MailAccount]]
  def save(account: MailAccount): RIO[ZConnection, Long]
  def loadMail(id: MailData.Id): RIO[ZConnection, Option[MailData]]
  def save(data: MailData): RIO[ZConnection, Long]
  def updateMailSettings(id: MailAccount.Id, settings: MailSettings): RIO[ZConnection, Long]
}

object MailRepository {
  inline def loadMails(offset: Option[Long], limit: Long): RIO[MailRepository & ZConnection, Chunk[MailData.Id]] =
    ZIO.serviceWithZIO[MailRepository](_.loadMails(offset, limit))

  inline def loadAccounts(): RIO[MailRepository & ZConnection, Chunk[MailAccount]] =
    ZIO.serviceWithZIO[MailRepository](_.loadAccounts())

  inline def loadAccount(key: MailAccount.Id): RIO[MailRepository & ZConnection, Option[MailAccount]] =
    ZIO.serviceWithZIO[MailRepository](_.loadAccount(key))

  inline def save(account: MailAccount): RIO[MailRepository & ZConnection, Long] =
    ZIO.serviceWithZIO[MailRepository](_.save(account))

  inline def loadMail(id: MailData.Id): RIO[MailRepository & ZConnection, Option[MailData]] =
    ZIO.serviceWithZIO[MailRepository](_.loadMail(id))

  inline def save(data: MailData): RIO[MailRepository & ZConnection, Long] =
    ZIO.serviceWithZIO[MailRepository](_.save(data))

  inline def updateMailSettings(id: MailAccount.Id, settings: MailSettings): RIO[MailRepository & ZConnection, Long] =
    ZIO.serviceWithZIO[MailRepository](_.updateMailSettings(id, settings))

  def live(): ZLayer[Tracing, Throwable, MailRepository] =
    ZLayer.fromFunction(tracing => new MailRepositoryLive().traced(tracing))

  class MailRepositoryLive() extends MailRepository { self =>
    def loadMails(offset: Option[Long], limit: Long): RIO[ZConnection, Chunk[MailData.Id]] =
      Queries.LOAD_MAILS(offset, limit).selectAll

    def loadAccounts(): RIO[ZConnection, Chunk[MailAccount]] =
      Queries.LOAD_ACCOUNTS().selectAll

    def loadAccount(key: MailAccount.Id): RIO[ZConnection, Option[MailAccount]] =
      Queries.LOAD_ACCOUNT(key).selectOne

    def save(account: MailAccount): RIO[ZConnection, Long] =
      Queries.SAVE_ACCOUNT(account).insert

    def loadMail(id: MailData.Id): RIO[ZConnection, Option[MailData]] =
      Queries.LOAD_MAIL(id).selectOne

    def save(data: MailData): RIO[ZConnection, Long] =
      Queries.SAVE_MAIL(data).insert

    def updateMailSettings(id: MailAccount.Id, settings: MailSettings): RIO[ZConnection, Long] =
      Queries.UPDATE_MAIL_SETTINGS(id, settings).update

    def traced(tracing: Tracing): MailRepository =
      new MailRepository {
        def loadMails(offset: Option[Long], limit: Long): RIO[ZConnection, Chunk[MailData.Id]] =
          self.loadMails(offset, limit) @@ tracing.aspects.span("MailRepository.loadMails")
        def loadAccounts(): RIO[ZConnection, Chunk[MailAccount]] =
          self.loadAccounts() @@ tracing.aspects.span("MailRepository.loadAccounts")
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

        def updateMailSettings(id: MailAccount.Id, settings: MailSettings): RIO[ZConnection, Long] =
          self.updateMailSettings(id, settings) @@ tracing.aspects.span(
            "MailRepository.updateMailSettings",
            attributes = Attributes(Attribute.long("accountId", id.asKey.value)),
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

    def LOAD_ACCOUNTS(): Query[MailAccount] =
      (sql"""
        SELECT key, type, name, email, settings, timestamp
        FROM mail_account
        """)
        .query[
          (
            MailAccount.Id,
            AccountType,
            MailAccount.Name,
            MailAccount.Email,
            MailSettings,
            Timestamp,
          ),
        ]
        .map(MailAccount.apply)

    def LOAD_ACCOUNT(key: MailAccount.Id): Query[MailAccount] =
      sql"""
    SELECT key, type, name, email, settings, timestamp
    FROM mail_account
    WHERE key = $key
    """.query[
        (
          MailAccount.Id,
          AccountType,
          MailAccount.Name,
          MailAccount.Email,
          MailSettings,
          Timestamp,
        ),
      ].map(MailAccount.apply)

    def SAVE_ACCOUNT(account: MailAccount): SqlFragment =
      val payload =
        java.util.Base64.getEncoder.encodeToString(summon[BinaryCodec[MailSettings]].encode(account.settings).toArray)

      sql"""
    INSERT INTO mail_account (key, type, email, name, settings, timestamp)
    VALUES (
      ${account.id},
      ${account.accountType},
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

    def UPDATE_MAIL_SETTINGS(id: MailAccount.Id, settings: MailSettings): SqlFragment =
      val payload =
        java.util.Base64.getEncoder.encodeToString(summon[BinaryCodec[MailSettings]].encode(settings).toArray)

      sql"""
        UPDATE mail_account
        SET settings = decode(${payload}, 'base64')
        WHERE key = $id
      """

  }

}

package com.youtoo
package mail
package service

import cats.implicits.*

import zio.*
import zio.jdbc.*
import zio.prelude.*

import com.youtoo.mail.model.*
import com.youtoo.job.model.*
import com.youtoo.mail.store.*
import com.youtoo.cqrs.*
import com.youtoo.postgres.*

import com.youtoo.mail.repository.*

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*

trait MailService {
  def startSync(accountKey: MailAccount.Id, labels: MailLabels, timestamp: Timestamp, jobId: Job.Id): Task[Unit]
  def grantAuthorization(accountKey: MailAccount.Id, token: TokenInfo, timestamp: Timestamp): Task[Unit]
  def revokeAuthorization(accountKey: MailAccount.Id, timestamp: Timestamp): Task[Unit]
  def recordSynced(
    accountKey: MailAccount.Id,
    timestamp: Timestamp,
    mailKeys: NonEmptyList[MailData.Id],
    token: MailToken,
    jobId: Job.Id,
  ): Task[Unit]
  def completeSync(accountKey: MailAccount.Id, timestamp: Timestamp, jobId: Job.Id): Task[Unit]

  def loadAccounts(): Task[Chunk[MailAccount]]
  def loadAccount(key: MailAccount.Id): Task[Option[MailAccount]]
  def loadState(accountKey: MailAccount.Id): Task[Option[Mail]]
  def save(account: MailAccount): Task[Long]

  def loadMail(id: MailData.Id): Task[Option[MailData]]
  def loadMails(offset: Option[Long], limit: Long): Task[Chunk[MailData.Id]]
  def save(data: MailData): Task[Long]
  def updateMailSettings(id: MailAccount.Id, settings: MailSettings): Task[Long]

}

object MailService {
  inline def startSync(
    accountKey: MailAccount.Id,
    labels: MailLabels,
    timestamp: Timestamp,
    jobId: Job.Id,
  ): RIO[MailService, Unit] =
    ZIO.serviceWithZIO(_.startSync(accountKey, labels, timestamp, jobId))

  inline def grantAuthorization(
    accountKey: MailAccount.Id,
    token: TokenInfo,
    timestamp: Timestamp,
  ): RIO[MailService, Unit] =
    ZIO.serviceWithZIO(_.grantAuthorization(accountKey, token, timestamp))

  inline def revokeAuthorization(accountKey: MailAccount.Id, timestamp: Timestamp): RIO[MailService, Unit] =
    ZIO.serviceWithZIO(_.revokeAuthorization(accountKey, timestamp))

  inline def recordSynced(
    accountKey: MailAccount.Id,
    timestamp: Timestamp,
    mailKeys: NonEmptyList[MailData.Id],
    token: MailToken,
    jobId: Job.Id,
  ): RIO[MailService, Unit] =
    ZIO.serviceWithZIO(_.recordSynced(accountKey, timestamp, mailKeys, token, jobId))

  inline def completeSync(accountKey: MailAccount.Id, timestamp: Timestamp, jobId: Job.Id): RIO[MailService, Unit] =
    ZIO.serviceWithZIO(_.completeSync(accountKey, timestamp, jobId))

  inline def loadAccounts(): RIO[MailService, Chunk[MailAccount]] =
    ZIO.serviceWithZIO(_.loadAccounts())

  inline def loadAccount(key: MailAccount.Id): RIO[MailService, Option[MailAccount]] =
    ZIO.serviceWithZIO(_.loadAccount(key))

  inline def save(account: MailAccount): RIO[MailService, Long] =
    ZIO.serviceWithZIO(_.save(account))

  inline def loadMail(id: MailData.Id): RIO[MailService, Option[MailData]] =
    ZIO.serviceWithZIO(_.loadMail(id))

  inline def loadState(accountKey: MailAccount.Id): RIO[MailService, Option[Mail]] =
    ZIO.serviceWithZIO(_.loadState(accountKey))

  inline def loadMails(offset: Option[Long], limit: Long): RIO[MailService, Chunk[MailData.Id]] =
    ZIO.serviceWithZIO(_.loadMails(offset, limit))

  inline def save(data: MailData): RIO[MailService, Long] =
    ZIO.serviceWithZIO(_.save(data))

  inline def updateMailSettings(id: MailAccount.Id, settings: MailSettings): RIO[MailService, Long] =
    ZIO.serviceWithZIO(_.updateMailSettings(id, settings))

  def live(): ZLayer[Tracing & ZConnectionPool & MailRepository & MailCQRS & MailEventStore, Throwable, MailService] =
    ZLayer.fromFunction {
      (
        pool: ZConnectionPool,
        repository: MailRepository,
        cqrs: MailCQRS,
        tracing: Tracing,
        eventStore: MailEventStore,
      ) =>
        MailServiceLive(pool, repository, cqrs, eventStore).traced(tracing)
    }

  class MailServiceLive(pool: ZConnectionPool, repository: MailRepository, cqrs: MailCQRS, eventStore: MailEventStore)
      extends MailService { self =>
    def startSync(accountKey: MailAccount.Id, labels: MailLabels, timestamp: Timestamp, jobId: Job.Id): Task[Unit] =
      val cmd = MailCommand.StartSync(labels, timestamp, jobId)
      cqrs.add(accountKey.asKey, cmd)

    def grantAuthorization(accountKey: MailAccount.Id, token: TokenInfo, timestamp: Timestamp): Task[Unit] =
      val cmd = MailCommand.GrantAuthorization(token, timestamp)
      cqrs.add(accountKey.asKey, cmd)

    def revokeAuthorization(accountKey: MailAccount.Id, timestamp: Timestamp): Task[Unit] =
      val cmd = MailCommand.RevokeAuthorization(timestamp)
      cqrs.add(accountKey.asKey, cmd)

    def recordSynced(
      accountKey: MailAccount.Id,
      timestamp: Timestamp,
      mailKeys: NonEmptyList[MailData.Id],
      token: MailToken,
      jobId: Job.Id,
    ): Task[Unit] =
      val cmd = MailCommand.RecordSync(timestamp, mailKeys, token, jobId)
      cqrs.add(accountKey.asKey, cmd)

    def completeSync(accountKey: MailAccount.Id, timestamp: Timestamp, jobId: Job.Id): Task[Unit] =
      val cmd = MailCommand.CompleteSync(timestamp, jobId)
      cqrs.add(accountKey.asKey, cmd)

    def loadAccounts(): Task[Chunk[MailAccount]] =
      repository.loadAccounts().atomically.provideEnvironment(ZEnvironment(pool))

    def loadAccount(key: MailAccount.Id): Task[Option[MailAccount]] =
      repository.loadAccount(key).atomically.provideEnvironment(ZEnvironment(pool))

    def save(account: MailAccount): Task[Long] =
      repository.save(account).atomically.provideEnvironment(ZEnvironment(pool))

    def loadMail(id: MailData.Id): Task[Option[MailData]] =
      repository.loadMail(id).atomically.provideEnvironment(ZEnvironment(pool))

    def loadState(accountKey: MailAccount.Id): Task[Option[Mail]] =
      (for {
        acc <- repository.loadAccount(accountKey)

        o <- acc match {
          case Some(acc) =>
            for {
              cursorEvents <- eventStore.readEvents(
                id = acc.id.asKey,
                query = PersistenceQuery.anyNamespace(MailEvent.NS.SyncStarted, MailEvent.NS.MailSynced),
                options = FetchOptions(),
              )

              cursor = cursorEvents.fold(None) { es =>
                EventHandler.applyEvents(es)(using MailEvent.LoadCursor())
              }

              authEvents <- eventStore.readEvents(
                id = acc.id.asKey,
                query =
                  PersistenceQuery.anyNamespace(MailEvent.NS.AuthorizationGranted, MailEvent.NS.AuthorizationRevoked),
                options = FetchOptions(),
              )
              authorization = authEvents.fold(Authorization.Pending()) { es =>
                EventHandler.applyEvents(es)(using MailEvent.LoadAuthorization())
              }
            } yield Mail(accountKey, cursor, authorization).some

          case None =>
            ZIO.none
        }

      } yield o).atomically.provideEnvironment(ZEnvironment(pool))

    def loadMails(offset: Option[Long], limit: Long): Task[Chunk[MailData.Id]] =
      repository.loadMails(offset, limit).atomically.provideEnvironment(ZEnvironment(pool))

    def save(data: MailData): Task[Long] =
      repository.save(data).atomically.provideEnvironment(ZEnvironment(pool))

    def updateMailSettings(id: MailAccount.Id, settings: MailSettings): Task[Long] =
      repository.updateMailSettings(id, settings).atomically.provideEnvironment(ZEnvironment(pool))

    def traced(tracing: Tracing): MailService =
      new MailService {
        def startSync(accountKey: MailAccount.Id, labels: MailLabels, timestamp: Timestamp, jobId: Job.Id): Task[Unit] =
          self.startSync(accountKey, labels, timestamp, jobId) @@ tracing.aspects.span(
            "MailService.startSync",
            attributes = Attributes(Attribute.long("accountId", accountKey.asKey.value)),
          )
        def grantAuthorization(
          accountKey: MailAccount.Id,
          token: TokenInfo,
          timestamp: Timestamp,
        ): Task[Unit] =
          self.grantAuthorization(accountKey, token, timestamp) @@ tracing.aspects.span(
            "MailService.grantAuthorization",
            attributes = Attributes(Attribute.long("accountId", accountKey.asKey.value)),
          )
        def revokeAuthorization(accountKey: MailAccount.Id, timestamp: Timestamp): Task[Unit] =
          self.revokeAuthorization(accountKey, timestamp) @@ tracing.aspects.span(
            "MailService.revokeAuthorization",
            attributes = Attributes(Attribute.long("accountId", accountKey.asKey.value)),
          )

        def recordSynced(
          accountKey: MailAccount.Id,
          timestamp: Timestamp,
          mailKeys: NonEmptyList[MailData.Id],
          token: MailToken,
          jobId: Job.Id,
        ): Task[Unit] =
          self.recordSynced(accountKey, timestamp, mailKeys, token, jobId) @@ tracing.aspects.span(
            "MailService.recordSynced",
            attributes = Attributes(Attribute.long("accountId", accountKey.asKey.value)),
          )
        def completeSync(accountKey: MailAccount.Id, timestamp: Timestamp, jobId: Job.Id): Task[Unit] =
          self.completeSync(accountKey, timestamp, jobId) @@ tracing.aspects.span(
            "MailService.completeSync",
            attributes = Attributes(Attribute.long("accountId", accountKey.asKey.value)),
          )
        def loadAccounts(): Task[Chunk[MailAccount]] =
          self.loadAccounts() @@ tracing.aspects.span("MailService.loadAccounts")
        def loadAccount(key: MailAccount.Id): Task[Option[MailAccount]] =
          self.loadAccount(key) @@ tracing.aspects.span(
            "MailService.loadAccount",
            attributes = Attributes(Attribute.long("accountId", key.asKey.value)),
          )
        def save(account: MailAccount): Task[Long] =
          self.save(account) @@ tracing.aspects.span(
            "MailService.save",
            attributes = Attributes(Attribute.long("accountId", account.id.asKey.value)),
          )
        def loadMail(id: MailData.Id): Task[Option[MailData]] =
          self.loadMail(id) @@ tracing.aspects.span(
            "MailService.loadMail",
            attributes = Attributes(Attribute.string("mailId", id.value)),
          )
        def loadState(accountKey: MailAccount.Id): Task[Option[Mail]] =
          self.loadState(accountKey) @@ tracing.aspects.span(
            "MailService.loadState",
            attributes = Attributes(Attribute.long("accountId", accountKey.asKey.value)),
          )
        def loadMails(offset: Option[Long], limit: Long): Task[Chunk[MailData.Id]] =
          self.loadMails(offset, limit) @@ tracing.aspects.span("MailService.loadMails")
        def save(data: MailData): Task[Long] =
          self.save(data) @@ tracing.aspects.span(
            "MailService.save",
            attributes = Attributes(Attribute.string("mailId", data.id.value)),
          )

        def updateMailSettings(id: MailAccount.Id, settings: MailSettings): Task[Long] =
          self.updateMailSettings(id, settings) @@ tracing.aspects.span(
            "MailService.updateMailSettings",
            attributes = Attributes(Attribute.long("accountId", id.asKey.value)),
          )
      }

  }

}

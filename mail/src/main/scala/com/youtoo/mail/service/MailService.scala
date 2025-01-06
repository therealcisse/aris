package com.youtoo
package mail
package service

import cats.implicits.*

import zio.interop.catz.core.*

import zio.*
import zio.jdbc.*
import zio.prelude.*

import com.youtoo.cqrs.*
import com.youtoo.sink.model.*
import com.youtoo.mail.model.*
import com.youtoo.job.model.*
import com.youtoo.mail.store.*
import com.youtoo.postgres.*

import com.youtoo.mail.repository.*

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*

trait MailService {
  def grantAuthorization(accountKey: MailAccount.Id, token: TokenInfo, timestamp: Timestamp): Task[Unit]
  def revokeAuthorization(accountKey: MailAccount.Id, timestamp: Timestamp): Task[Unit]

  def startSync(accountKey: MailAccount.Id, labels: MailLabels, timestamp: Timestamp, jobId: Job.Id): Task[Unit]
  def recordSynced(
    accountKey: MailAccount.Id,
    timestamp: Timestamp,
    mailKeys: NonEmptyList[MailData.Id],
    token: MailToken,
    jobId: Job.Id,
  ): Task[Unit]
  def completeSync(accountKey: MailAccount.Id, timestamp: Timestamp, jobId: Job.Id): Task[Unit]

  def setAutoSync(accountKey: MailAccount.Id, schedule: SyncConfig.CronExpression): Task[Unit]
  def disableAutoSync(accountKey: MailAccount.Id): Task[Unit]
  def setAuthConfig(accountKey: MailAccount.Id, config: AuthConfig): Task[Unit]
  def linkSink(accountKey: MailAccount.Id, sinkId: SinkDefinition.Id): Task[Unit]
  def unlinkSink(accountKey: MailAccount.Id, sinkId: SinkDefinition.Id): Task[Unit]

  def loadAccounts(): Task[Chunk[MailAccount]]
  def loadAccount(key: MailAccount.Id): Task[Option[MailAccount]]
  def loadState(accountKey: MailAccount.Id): Task[Option[Mail]]
  def loadDownloadState(accountKey: MailAccount.Id): Task[Option[Download]]
  def save(id: MailAccount.Id, info: MailAccount.Information): Task[Long]

  def loadMail(id: MailData.Id): Task[Option[MailData]]
  def loadMails(offset: Option[Long], limit: Long): Task[Chunk[MailData.Id]]
  def save(data: MailData): Task[Long]
  def saveMails(data: NonEmptyList[MailData]): Task[Long]

}

object MailService {
  inline def grantAuthorization(
    accountKey: MailAccount.Id,
    token: TokenInfo,
    timestamp: Timestamp,
  ): RIO[MailService, Unit] =
    ZIO.serviceWithZIO(_.grantAuthorization(accountKey, token, timestamp))

  inline def revokeAuthorization(accountKey: MailAccount.Id, timestamp: Timestamp): RIO[MailService, Unit] =
    ZIO.serviceWithZIO(_.revokeAuthorization(accountKey, timestamp))

  inline def startSync(
    accountKey: MailAccount.Id,
    labels: MailLabels,
    timestamp: Timestamp,
    jobId: Job.Id,
  ): RIO[MailService, Unit] =
    ZIO.serviceWithZIO(_.startSync(accountKey, labels, timestamp, jobId))

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

  inline def setAutoSync(accountKey: MailAccount.Id, schedule: SyncConfig.CronExpression): RIO[MailService, Unit] =
    ZIO.serviceWithZIO(_.setAutoSync(accountKey, schedule))

  inline def disableAutoSync(accountKey: MailAccount.Id): RIO[MailService, Unit] =
    ZIO.serviceWithZIO(_.disableAutoSync(accountKey))

  inline def setAuthConfig(accountKey: MailAccount.Id, config: AuthConfig): RIO[MailService, Unit] =
    ZIO.serviceWithZIO(_.setAuthConfig(accountKey, config))

  inline def linkSink(accountKey: MailAccount.Id, sinkId: SinkDefinition.Id): RIO[MailService, Unit] =
    ZIO.serviceWithZIO(_.linkSink(accountKey, sinkId))

  inline def unlinkSink(accountKey: MailAccount.Id, sinkId: SinkDefinition.Id): RIO[MailService, Unit] =
    ZIO.serviceWithZIO(_.unlinkSink(accountKey, sinkId))

  inline def loadAccounts(): RIO[MailService, Chunk[MailAccount]] =
    ZIO.serviceWithZIO(_.loadAccounts())

  inline def loadAccount(key: MailAccount.Id): RIO[MailService, Option[MailAccount]] =
    ZIO.serviceWithZIO(_.loadAccount(key))

  inline def save(id: MailAccount.Id, info: MailAccount.Information): RIO[MailService, Long] =
    ZIO.serviceWithZIO(_.save(id, info))

  inline def loadMail(id: MailData.Id): RIO[MailService, Option[MailData]] =
    ZIO.serviceWithZIO(_.loadMail(id))

  inline def loadState(accountKey: MailAccount.Id): RIO[MailService, Option[Mail]] =
    ZIO.serviceWithZIO(_.loadState(accountKey))

  inline def loadDownloadState(accountKey: MailAccount.Id): RIO[MailService, Option[Download]] =
    ZIO.serviceWithZIO(_.loadDownloadState(accountKey))

  inline def loadMails(offset: Option[Long], limit: Long): RIO[MailService, Chunk[MailData.Id]] =
    ZIO.serviceWithZIO(_.loadMails(offset, limit))

  inline def save(data: MailData): RIO[MailService, Long] =
    ZIO.serviceWithZIO(_.save(data))

  inline def saveMails(data: NonEmptyList[MailData]): RIO[MailService, Long] =
    ZIO.serviceWithZIO(_.saveMails(data))

  def live(): ZLayer[
    Tracing & ZConnectionPool & MailRepository & MailCQRS & MailConfigEventStore & MailConfigCQRS & AuthorizationCQRS & MailEventStore & DownloadEventStore & AuthorizationEventStore,
    Throwable,
    MailService,
  ] =
    ZLayer.fromFunction {
      (
        pool: ZConnectionPool,
        repository: MailRepository,
        mailCQRS: MailCQRS,
        configCQRS: MailConfigCQRS,
        authorizationCQRS: AuthorizationCQRS,
        tracing: Tracing,
        mailStore: MailEventStore,
        downloadStore: DownloadEventStore,
        configStore: MailConfigEventStore,
        authorizationStore: AuthorizationEventStore,
      ) =>
        MailServiceLive(
          pool,
          repository,
          mailCQRS,
          configCQRS,
          authorizationCQRS,
          mailStore,
          downloadStore,
          configStore,
          authorizationStore,
        ).traced(tracing)
    }

  class MailServiceLive(
    pool: ZConnectionPool,
    repository: MailRepository,
    mailCQRS: MailCQRS,
    configCQRS: MailConfigCQRS,
    authorizationCQRS: AuthorizationCQRS,
    mailStore: MailEventStore,
    downloadStore: DownloadEventStore,
    configStore: MailConfigEventStore,
    authorizationStore: AuthorizationEventStore,
  ) extends MailService { self =>
    def grantAuthorization(accountKey: MailAccount.Id, token: TokenInfo, timestamp: Timestamp): Task[Unit] =
      val cmd = AuthorizationCommand.GrantAuthorization(token, timestamp)
      authorizationCQRS.add(accountKey.asKey, cmd)

    def revokeAuthorization(accountKey: MailAccount.Id, timestamp: Timestamp): Task[Unit] =
      val cmd = AuthorizationCommand.RevokeAuthorization(timestamp)
      authorizationCQRS.add(accountKey.asKey, cmd)

    def startSync(accountKey: MailAccount.Id, labels: MailLabels, timestamp: Timestamp, jobId: Job.Id): Task[Unit] =
      val cmd = MailCommand.StartSync(labels, timestamp, jobId)
      mailCQRS.add(accountKey.asKey, cmd)

    def recordSynced(
      accountKey: MailAccount.Id,
      timestamp: Timestamp,
      mailKeys: NonEmptyList[MailData.Id],
      token: MailToken,
      jobId: Job.Id,
    ): Task[Unit] =
      val cmd = MailCommand.RecordSync(timestamp, mailKeys, token, jobId)
      mailCQRS.add(accountKey.asKey, cmd)

    def completeSync(accountKey: MailAccount.Id, timestamp: Timestamp, jobId: Job.Id): Task[Unit] =
      val cmd = MailCommand.CompleteSync(timestamp, jobId)
      mailCQRS.add(accountKey.asKey, cmd)

    def setAutoSync(accountKey: MailAccount.Id, schedule: SyncConfig.CronExpression): Task[Unit] =
      val cmd = MailConfigCommand.EnableAutoSync(schedule)
      configCQRS.add(accountKey.asKey, cmd)

    def disableAutoSync(accountKey: MailAccount.Id): Task[Unit] =
      val cmd = MailConfigCommand.DisableAutoSync()
      configCQRS.add(accountKey.asKey, cmd)

    def setAuthConfig(accountKey: MailAccount.Id, config: AuthConfig): Task[Unit] =
      val cmd = MailConfigCommand.SetAuthConfig(config)
      configCQRS.add(accountKey.asKey, cmd)

    def linkSink(accountKey: MailAccount.Id, sinkId: SinkDefinition.Id): Task[Unit] =
      val cmd = MailConfigCommand.LinkSink(sinkId)
      configCQRS.add(accountKey.asKey, cmd)

    def unlinkSink(accountKey: MailAccount.Id, sinkId: SinkDefinition.Id): Task[Unit] =
      val cmd = MailConfigCommand.UnlinkSink(sinkId)
      configCQRS.add(accountKey.asKey, cmd)

    def loadAccounts(): Task[Chunk[MailAccount]] =
      for {
        ids <- repository.loadAccounts().atomically.provideEnvironment(ZEnvironment(pool))

        accs <- ZIO.foreach(ids) { id =>
          loadAccount(id)
        }
      } yield (accs.sequence getOrElse Chunk.empty)

    def loadAccount(key: MailAccount.Id): Task[Option[MailAccount]] =
      atomically {

        for {
          data <- repository.loadAccount(key)

          configEvents <- configStore.readEvents(
            id = key.asKey,
            query = PersistenceQuery.anyNamespace(
              MailConfigEvent.NS.AutoSyncEnabled,
              MailConfigEvent.NS.AutoSyncDisabled,
              MailConfigEvent.NS.AuthConfigSet,
              MailConfigEvent.NS.SinkLinked,
              MailConfigEvent.NS.SinkUnlinked,
            ),
            options = FetchOptions(),
          )

          config = configEvents.fold(None) { es =>
            EventHandler.applyEvents(es)(using MailConfigEvent.LoadMailConfig())
          }

        } yield data.map(_.toAccount(key, config getOrElse MailConfig.default))

      }.provideEnvironment(ZEnvironment(pool))

    def save(id: MailAccount.Id, info: MailAccount.Information): Task[Long] =
      repository.save(id, info).atomically.provideEnvironment(ZEnvironment(pool))

    def loadMail(id: MailData.Id): Task[Option[MailData]] =
      repository.loadMail(id).atomically.provideEnvironment(ZEnvironment(pool))

    def loadState(accountKey: MailAccount.Id): Task[Option[Mail]] =
      atomically {
        val cursor = for {
          cursorEvents <- mailStore.readEvents(
            id = accountKey.asKey,
            query = PersistenceQuery.anyNamespace(MailEvent.NS.SyncStarted, MailEvent.NS.MailSynced),
            options = FetchOptions(),
          )

          cursor = cursorEvents.fold(None) { es =>
            EventHandler.applyEvents(es)(using MailEvent.LoadCursor())
          }

        } yield cursor

        val authorization = for {
          authEvents <- authorizationStore.readEvents(
            id = accountKey.asKey,
            query = PersistenceQuery
              .anyNamespace(AuthorizationEvent.NS.AuthorizationGranted, AuthorizationEvent.NS.AuthorizationRevoked),
            options = FetchOptions().desc().limit(1L),
          )
          authorization = authEvents.fold(Authorization.Pending()) { es =>
            EventHandler.applyEvents(es)(using AuthorizationEvent.LoadAuthorization())
          }
        } yield authorization

        (cursor <&> authorization) map { case (cursor, authorization) =>
          Mail(accountKey, cursor, authorization).some
        }

      }.provideEnvironment(ZEnvironment(pool))

    def loadDownloadState(accountKey: MailAccount.Id): Task[Option[Download]] =
      atomically {

        val lastVersion = for {
          events <- downloadStore.readEvents(
            id = accountKey.asKey,
            query = PersistenceQuery.ns(DownloadEvent.NS.Downloaded),
            options = FetchOptions().desc().limit(1L),
          )

          version = events.fold(None) { es =>
            EventHandler.applyEvents(es)(using DownloadEvent.LoadVersion()).some
          }

        } yield version

        val authorization = for {
          authEvents <- authorizationStore.readEvents(
            id = accountKey.asKey,
            query = PersistenceQuery
              .anyNamespace(AuthorizationEvent.NS.AuthorizationGranted, AuthorizationEvent.NS.AuthorizationRevoked),
            options = FetchOptions().desc().limit(1L),
          )
          authorization = authEvents.fold(Authorization.Pending()) { es =>
            EventHandler.applyEvents(es)(using AuthorizationEvent.LoadAuthorization())
          }
        } yield authorization

        (lastVersion <&> authorization) map { case (version, authorization) =>
          Download(accountKey, version, authorization).some
        }
      }.provideEnvironment(ZEnvironment(pool))

    def loadMails(offset: Option[Long], limit: Long): Task[Chunk[MailData.Id]] =
      repository.loadMails(offset, limit).atomically.provideEnvironment(ZEnvironment(pool))

    def save(data: MailData): Task[Long] =
      repository.save(data).atomically.provideEnvironment(ZEnvironment(pool))

    def saveMails(data: NonEmptyList[MailData]): Task[Long] =
      repository.saveMails(data).atomically.provideEnvironment(ZEnvironment(pool))

    def traced(tracing: Tracing): MailService =
      new MailService {
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

        def startSync(accountKey: MailAccount.Id, labels: MailLabels, timestamp: Timestamp, jobId: Job.Id): Task[Unit] =
          self.startSync(accountKey, labels, timestamp, jobId) @@ tracing.aspects.span(
            "MailService.startSync",
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
        def setAutoSync(accountKey: MailAccount.Id, schedule: SyncConfig.CronExpression): Task[Unit] =
          self.setAutoSync(accountKey, schedule) @@ tracing.aspects.span(
            "MailService.setAutoSync",
            attributes = Attributes(Attribute.long("accountId", accountKey.asKey.value)),
          )
        def disableAutoSync(accountKey: MailAccount.Id): Task[Unit] =
          self.disableAutoSync(accountKey) @@ tracing.aspects.span(
            "MailService.disableAutoSync",
            attributes = Attributes(Attribute.long("accountId", accountKey.asKey.value)),
          )
        def setAuthConfig(accountKey: MailAccount.Id, config: AuthConfig): Task[Unit] =
          self.setAuthConfig(accountKey, config) @@ tracing.aspects.span(
            "MailService.setAuthConfig",
            attributes = Attributes(Attribute.long("accountId", accountKey.asKey.value)),
          )
        def linkSink(accountKey: MailAccount.Id, sinkId: SinkDefinition.Id): Task[Unit] =
          self.linkSink(accountKey, sinkId) @@ tracing.aspects.span(
            "MailService.linkSink",
            attributes = Attributes(Attribute.long("accountId", accountKey.asKey.value)),
          )
        def unlinkSink(accountKey: MailAccount.Id, sinkId: SinkDefinition.Id): Task[Unit] =
          self.unlinkSink(accountKey, sinkId) @@ tracing.aspects.span(
            "MailService.unlinkSink",
            attributes = Attributes(Attribute.long("accountId", accountKey.asKey.value)),
          )

        def loadAccounts(): Task[Chunk[MailAccount]] =
          self.loadAccounts() @@ tracing.aspects.span("MailService.loadAccounts")
        def loadAccount(key: MailAccount.Id): Task[Option[MailAccount]] =
          self.loadAccount(key) @@ tracing.aspects.span(
            "MailService.loadAccount",
            attributes = Attributes(Attribute.long("accountId", key.asKey.value)),
          )
        def save(id: MailAccount.Id, info: MailAccount.Information): Task[Long] =
          self.save(id, info) @@ tracing.aspects.span(
            "MailService.save",
            attributes = Attributes(Attribute.long("accountId", id.asKey.value)),
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
        def loadDownloadState(accountKey: MailAccount.Id): Task[Option[Download]] =
          self.loadDownloadState(accountKey) @@ tracing.aspects.span(
            "MailService.loadDownloadState",
            attributes = Attributes(Attribute.long("accountId", accountKey.asKey.value)),
          )
        def loadMails(offset: Option[Long], limit: Long): Task[Chunk[MailData.Id]] =
          self.loadMails(offset, limit) @@ tracing.aspects.span("MailService.loadMails")
        def save(data: MailData): Task[Long] =
          self.save(data) @@ tracing.aspects.span(
            "MailService.save",
            attributes = Attributes(Attribute.string("mailId", data.id.value)),
          )
        def saveMails(data: NonEmptyList[MailData]): Task[Long] =
          self.saveMails(data) @@ tracing.aspects.span(
            "MailService.saveMails",
            attributes = Attributes(Attribute.long("mails", data.size.toLong)),
          )

      }

  }

}

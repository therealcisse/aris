package com.youtoo
package mail
package service

import zio.*
import zio.jdbc.*

import com.youtoo.mail.model.*
import com.youtoo.job.model.*
import com.youtoo.cqrs.*
import com.youtoo.postgres.*

import com.youtoo.mail.repository.*

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*

trait MailService {
  def startSync(accountKey: MailAccount.Id, labels: MailLabels, timestamp: Timestamp, jobId: Job.Id): Task[Unit]
  def recordSynced(accountKey: MailAccount.Id, timestamp: Timestamp, mailKeys: List[MailData.Id], token: Option[MailToken], jobId: Job.Id): Task[Unit]
  def completeSync(accountKey: MailAccount.Id, timestamp: Timestamp, jobId: Job.Id): Task[Unit]

  def loadAccounts(options: FetchOptions): Task[Chunk[MailAccount]]
  def loadAccount(key: MailAccount.Id): Task[Option[MailAccount]]
  def save(account: MailAccount): Task[Long]
  def loadMail(id: MailData.Id): Task[Option[MailData]]
  def loadMails(options: FetchOptions): Task[Chunk[MailData]]
  def save(data: MailData): Task[Long]

}

object MailService {
  inline def startImport(accountKey: MailAccount.Id, labels: MailLabels, timestamp: Timestamp, jobId: Job.Id): RIO[MailService & ZConnection, Unit] =
    ZIO.serviceWithZIO[MailService](_.startSync(accountKey, labels, timestamp, jobId))

  inline def recordImport(accountKey: MailAccount.Id, timestamp: Timestamp, mailKeys: List[MailData.Id], token: Option[MailToken], jobId: Job.Id): RIO[MailService & ZConnection, Unit] =
    ZIO.serviceWithZIO[MailService](_.recordSynced(accountKey, timestamp, mailKeys, token, jobId))

  inline def completeImport(accountKey: MailAccount.Id, timestamp: Timestamp, jobId: Job.Id): RIO[MailService & ZConnection, Unit] =
    ZIO.serviceWithZIO[MailService](_.completeSync(accountKey, timestamp, jobId))

  inline def loadAccounts(options: FetchOptions): RIO[MailService, Chunk[MailAccount]] =
    ZIO.serviceWithZIO[MailService](_.loadAccounts(options))

  inline def loadAccount(key: MailAccount.Id): RIO[MailService, Option[MailAccount]] =
    ZIO.serviceWithZIO[MailService](_.loadAccount(key))

  inline def save(account: MailAccount): RIO[MailService & ZConnection, Long] =
    ZIO.serviceWithZIO[MailService](_.save(account))

  inline def loadMail(id: MailData.Id): RIO[MailService, Option[MailData]] =
    ZIO.serviceWithZIO[MailService](_.loadMail(id))

  inline def loadMails(options: FetchOptions): RIO[MailService, Chunk[MailData]] =
    ZIO.serviceWithZIO[MailService](_.loadMails(options))

  inline def save(data: MailData): RIO[MailService & ZConnection, Long] =
    ZIO.serviceWithZIO[MailService](_.save(data))


  def live(): ZLayer[Tracing & ZConnectionPool & MailRepository & MailCQRS, Throwable, MailService] =
    ZLayer.fromFunction { (
      pool: ZConnectionPool,
      repository: MailRepository,
      cqrs: MailCQRS,
        tracing: Tracing,
    ) =>

      MailServiceLive(pool, repository, cqrs).traced(tracing)
    }

  class MailServiceLive(pool: ZConnectionPool, repository: MailRepository, cqrs: MailCQRS) extends MailService { self =>
    def startSync(accountKey: MailAccount.Id, labels: MailLabels, timestamp: Timestamp, jobId: Job.Id): Task[Unit] =
      val cmd = MailCommand.StartSync(labels, timestamp, jobId)
      cqrs.add(accountKey.asKey, cmd)

    def recordSynced(accountKey: MailAccount.Id, timestamp: Timestamp, mailKeys: List[MailData.Id], token: Option[MailToken], jobId: Job.Id): Task[Unit] =
      val cmd = MailCommand.RecordSync(timestamp, mailKeys, token, jobId)
      cqrs.add(accountKey.asKey, cmd)

    def completeSync(accountKey: MailAccount.Id, timestamp: Timestamp, jobId: Job.Id): Task[Unit] =
      val cmd = MailCommand.CompleteSync(timestamp, jobId)
      cqrs.add(accountKey.asKey, cmd)

    def loadAccounts(options: FetchOptions): Task[Chunk[MailAccount]] =
      repository.loadAccounts(options).atomically.provideEnvironment(ZEnvironment(pool))

    def loadAccount(key: MailAccount.Id): Task[Option[MailAccount]] =
      repository.loadAccount(key).atomically.provideEnvironment(ZEnvironment(pool))

    def save(account: MailAccount): Task[Long] =
      repository.save(account).atomically.provideEnvironment(ZEnvironment(pool))

    def loadMail(id: MailData.Id): Task[Option[MailData]] =
      repository.loadMail(id).atomically.provideEnvironment(ZEnvironment(pool))

    def loadMails(options: FetchOptions): Task[Chunk[MailData]] =
      repository.loadMails(options).atomically.provideEnvironment(ZEnvironment(pool))

    def save(data: MailData): Task[Long] =
      repository.save(data).atomically.provideEnvironment(ZEnvironment(pool))

    def traced(tracing: Tracing): MailService =
      new MailService {
        def startSync(accountKey: MailAccount.Id, labels: MailLabels, timestamp: Timestamp, jobId: Job.Id): Task[Unit] =
          self.startSync(accountKey, labels, timestamp, jobId) @@ tracing.aspects.span(
            "MailService.startImport",
            attributes = Attributes(Attribute.long("accountId", accountKey.asKey.value)),
          )
        def recordSynced(accountKey: MailAccount.Id, timestamp: Timestamp, mailKeys: List[MailData.Id], token: Option[MailToken], jobId: Job.Id): Task[Unit] =
          self.recordSynced(accountKey, timestamp, mailKeys, token, jobId) @@ tracing.aspects.span(
            "MailService.recordImport",
            attributes = Attributes(Attribute.long("accountId", accountKey.asKey.value)),
          )
        def completeSync(accountKey: MailAccount.Id, timestamp: Timestamp, jobId: Job.Id): Task[Unit] =
          self.completeSync(accountKey, timestamp, jobId) @@ tracing.aspects.span(
            "MailService.completeImport",
            attributes = Attributes(Attribute.long("accountId", accountKey.asKey.value)),
          )
        def loadAccounts(options: FetchOptions): Task[Chunk[MailAccount]] =
          self.loadAccounts(options) @@ tracing.aspects.span("MailService.loadAccounts")
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
        def loadMails(options: FetchOptions): Task[Chunk[MailData]] =
          self.loadMails(options) @@ tracing.aspects.span("MailService.loadMails")
        def save(data: MailData): Task[Long] =
          self.save(data) @@ tracing.aspects.span(
            "MailService.save",
            attributes = Attributes(Attribute.string("mailId", data.id.value)),
          )
      }

  }

}


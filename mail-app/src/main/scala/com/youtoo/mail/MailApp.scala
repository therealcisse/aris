package com.youtoo
package mail

import scala.language.future

import zio.*
import zio.jdbc.*

import com.youtoo.postgres.*
import com.youtoo.cqrs.*
import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.service.*

import com.youtoo.mail.input.*
import com.youtoo.mail.model.*
import com.youtoo.mail.service.*
import com.youtoo.mail.integration.*
import com.youtoo.mail.repository.*
import com.youtoo.mail.store.*
import com.youtoo.postgres.config.*
import com.youtoo.job.service.*
import com.youtoo.job.store.*
import com.youtoo.job.repository.*
import com.youtoo.job.*
import com.youtoo.lock.*
import com.youtoo.lock.repository.*

import zio.json.*

import zio.http.{Version as _, *}
import zio.http.netty.NettyConfig

import com.youtoo.observability.RestEndpoint
import com.youtoo.observability.RestEndpoint.*
import com.youtoo.observability.otel.OtelSdk

import zio.telemetry.opentelemetry.metrics.*
import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.baggage.Baggage

import com.youtoo.mail.integration.internal.GmailSupport

import com.youtoo.std.*

object MailApplication extends MailApp(8181) {}

trait MailApp(val port: Int) extends ZIOApp, JsonSupport {
  import com.youtoo.cqrs.Codecs.json.given

  inline val FetchSize = 1_000L

  type Environment =
    FlywayMigration & ZConnectionPool & CQRSPersistence & SnapshotStore & MailEventStore & MailCQRS & Server & Server.Config & NettyConfig & MailService & SyncService & MailClient & GmailPool & MailRepository & JobService & JobRepository & JobEventStore & JobCQRS & LockManager & LockRepository & SnapshotStrategy.Factory & Tracing & Baggage & Meter

  given environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  private val config = Server.Config.default
    .port(port)

  private val nettyConfig = NettyConfig.default
    .leakDetection(NettyConfig.LeakDetectionLevel.DISABLED)

  private val configLayer = ZLayer.succeed(config)
  private val nettyConfigLayer = ZLayer.succeed(nettyConfig)

  private val instrumentationScopeName = "com.youtoo.mail.MailApp"
  private val resourceName = "mail"

  val bootstrap: ZLayer[Any, Nothing, Environment] =
    Scope.default ++ Log.layer >>> Runtime.disableFlags(
      RuntimeFlag.FiberRoots,
    ) ++ Runtime.enableRuntimeMetrics ++ Runtime.enableAutoBlockingExecutor ++ Runtime.enableFlags(
      RuntimeFlag.EagerShiftBack,
    ) ++
      ZLayer
        .makeSome[Scope, Environment](
          zio.metrics.jvm.DefaultJvmMetrics.live.unit,
          DatabaseConfig.pool,
          com.youtoo.cqrs.service.postgres.PostgresCQRSPersistence.live(),
          // com.youtoo.cqrs.service.memory.MemoryCQRSPersistence.live(),
          FlywayMigration.live(),
          SnapshotStore.live(),
          MailEventStore.live(),
          MailService.live(),
          SyncService.live(),
          JobService.live(),
          JobRepository.live(),
          JobEventStore.live(),
          JobCQRS.live(),
          LockManager.live(),
          LockRepository.memory(),
          MailRepository.live(),
          MailCQRS.live(),
          configLayer,
          nettyConfigLayer,
          Server.customized,
          SnapshotStrategy.live(),
          MailClient.live(),
          GmailPool.live(),
          OtelSdk.custom(resourceName),
          OpenTelemetry.tracing(instrumentationScopeName),
          OpenTelemetry.metrics(instrumentationScopeName),
          OpenTelemetry.logging(instrumentationScopeName),
          OpenTelemetry.baggage(),
          // OpenTelemetry.zioMetrics,
          OpenTelemetry.contextZIO,
        )
        .orDie ++ Runtime.setConfigProvider(ConfigProvider.envProvider)

  val endpoint = RestEndpoint(RestEndpoint.Service("mail"))

  val routes: Routes[Scope & Environment, Response] = Routes(
    Method.GET / "mail" / "health" -> handler(Response.json(ProjectInfo.toJson)),
    Method.GET / "mail-accounts" -> handler { (req: Request) =>
      endpoint.boundary("get_mail_accounts", req) {
        getAllMailAccounts() map { mailAccounts => Response.json(mailAccounts.toJson) }
      }
    },
    Method.POST / "mail-accounts" / long("accountId") / "authenticate" -> handler { (accountId: Long, req: Request) =>
      endpoint.boundary("authenticate_mail_account", req) {
        req.body.fromBody[String] flatMap { authorizationCode =>
          authenticateMailAccount(MailAccount.Id(Key(accountId)), authorizationCode = authorizationCode) map { _ =>
            Response.ok
          }
        }

      }
    },
    Method.POST / "mail-accounts" / "gmail" -> handler { (req: Request) =>
      endpoint.boundary("add_gmail_account", req) {
        for {
          account <- req.body.fromBody[CreateGmailAccountRequest]
          accountId <- addGmailAccount(account)
        } yield Response.json(s"""{"id":"$accountId"}""")
      }
    },
    Method.GET / "mail-accounts" / long("accountId") -> handler { (accountId: Long, req: Request) =>
      endpoint.boundary("get_mail_account", req) {
        getMailAccount(MailAccount.Id(Key(accountId))) map {
          case Some(account) => Response.json(account.toJson)
          case None => Response.notFound
        }
      }
    },
    Method.PUT / "mail-accounts" / long("accountId") / "sync-settings" / "toggle-auto-sync" -> handler {
      (accountId: Long, req: Request) =>
        endpoint.boundary("toggle_mail_account_sync_auto", req) {
          req.body.fromBody[Boolean] flatMap { autoSync =>
            updateMailSettings(MailAccount.Id(Key(accountId)), autoSync = Some(autoSync)) map { _ => Response.ok }
          }
        }
    },
    Method.PUT / "mail-accounts" / long("accountId") / "sync-settings" / "auto-sync-schedule" -> handler {
      (accountId: Long, req: Request) =>
        endpoint.boundary("update_mail_account_auto_sync_schedule", req) {
          req.body.fromBody[String] flatMap { schedule =>
            updateMailSettings(MailAccount.Id(Key(accountId)), schedule = Some(schedule)) map { _ => Response.ok }
          }
        }
    },
    Method.GET / "mail-data" / long("accountId") -> handler { (accountId: Long, req: Request) =>
      endpoint.boundary("get_mail_data", req) {
        getAllMailData() map { mailData => Response.json(mailData.toJson) }
      }
    },
    Method.GET / "mail-data" / string("mailId") -> handler { (mailId: String, req: Request) =>
      endpoint.boundary("get_mail_data", req) {
        getMailData(MailData.Id(mailId)) map {
          case Some(data) => Response.json(data.toJson)
          case None => Response.notFound
        }
      }
    },
    Method.GET / "mail-state" / long("accountId") -> handler { (accountId: Long, req: Request) =>
      endpoint.boundary("get_mail_state", req) {
        getMailState(MailAccount.Id(Key(accountId))) map {
          case Some(state) => Response.json(state.toJson)
          case None => Response.notFound
        }
      }
    },
    Method.POST / "mail-sync" / long("accountId") -> handler { (accountId: Long, req: Request) =>
      endpoint.boundary("trigger_mail_sync", req) {
        triggerMailSync(MailAccount.Id(Key(accountId))) `as` Response.ok
      }
    },
  )

  def addGmailAccount(request: CreateGmailAccountRequest): RIO[Environment, MailAccount.Id] =
    for {
      id <- MailAccount.Id.gen
      timestamp <- Timestamp.gen

      account = MailAccount(
        id = id,
        accountType = AccountType.Gmail,
        name = request.name,
        email = request.email,
        settings = MailSettings(authConfig = AuthConfig(), syncConfig = request.syncConfig),
        timestamp = timestamp,
      )

      _ <- MailService.save(account)

      clientInfo <- ZIO.config[GoogleClientInfo]

      info <- GmailSupport.getToken(clientInfo, request.authorizationCode).either

      _ <- info match {
        case Left(e) =>
          Log.error(s"Authentication failed for account ${account.id} : ${e.getMessage}", e) *> MailService
            .revokeAuthorization(account.id, timestamp)
        case Right(token) => MailService.grantAuthorization(account.id, token, timestamp)
      }

    } yield id

  def getMailAccount(id: MailAccount.Id): RIO[Environment, Option[MailAccount]] = MailService.loadAccount(id)

  def authenticateMailAccount(
    id: MailAccount.Id,
    authorizationCode: String,
  ): RIO[Environment, Unit] = for {
    acc <- MailService.loadAccount(id)

    _ <- acc match {
      case Some(acc) =>
        acc.accountType match {
          case AccountType.Gmail =>
            for {
              clientInfo <- ZIO.config[GoogleClientInfo]

              info <- GmailSupport.getToken(clientInfo, authorizationCode).either

              timestamp <- Timestamp.gen

              _ <- info match {
                case Left(e) =>
                  Log.error(s"Authentication failed for account ${acc.id} : ${e.getMessage}", e) *> MailService
                    .revokeAuthorization(acc.id, timestamp)
                case Right(token) => MailService.grantAuthorization(acc.id, token, timestamp)
              }

            } yield ()

        }

      case None => Log.error(s"Account not found $id")
    }

  } yield ()

  def updateMailSettings(
    id: MailAccount.Id,
    schedule: Option[String] = None,
    autoSync: Option[Boolean] = None,
  ): RIO[Environment, Unit] = for {
    acc <- MailService.loadAccount(id)

    _ <- acc match {
      case Some(acc) =>
        (schedule, autoSync) match {
          case (Some(cron), None) =>
            val syncConfig = SyncConfig(
              autoSync = SyncConfig.AutoSync.enabled(SyncConfig.CronExpression(cron)),
            )

            MailService.updateMailSettings(acc.id, acc.settings.copy(syncConfig = syncConfig))

          case (None, Some(autoSync)) =>
            val syncConfig = SyncConfig(
              autoSync =
                if autoSync then
                  (acc.settings.syncConfig match {
                    case SyncConfig(SyncConfig.AutoSync.disabled(Some(cron))) => SyncConfig.AutoSync.enabled(cron)
                    case SyncConfig(autoSync) => autoSync
                  })
                else
                  SyncConfig.AutoSync.disabled(acc.settings.syncConfig match {
                    case SyncConfig(SyncConfig.AutoSync.enabled(cron)) => Some(cron)
                    case _ => None
                  }),
            )

            MailService.updateMailSettings(acc.id, acc.settings.copy(syncConfig = syncConfig))

          case _ => ZIO.unit
        }

      case None => Log.error(s"Account not found $id")
    }

  } yield ()

  def getAllMailAccounts(): RIO[Environment, Chunk[MailAccount]] = MailService.loadAccounts(FetchOptions())

  def getAllMailData(): RIO[Environment, Chunk[MailData.Id]] = MailService.loadMails(FetchOptions())

  def getMailData(id: MailData.Id): RIO[Environment, Option[MailData]] = MailService.loadMail(id)

  def getMailState(id: MailAccount.Id): RIO[Environment, Option[Mail]] = MailService.loadState(id)

  def triggerMailSync(id: MailAccount.Id): RIO[Scope & Environment, ?] =
    SyncService.sync(id).forkScoped

  def run: RIO[Environment & Scope, Unit] =
    for {
      _ <- endpoint.uptime

      config <- ZIO.config[DatabaseConfig]
      _ <- FlywayMigration.run(config)

      _ <- Server.serve(routes)
    } yield ()

}

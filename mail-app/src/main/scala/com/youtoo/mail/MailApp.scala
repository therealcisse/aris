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
import com.youtoo.cqrs.service.memory.*
import com.youtoo.mail.store.*
import com.youtoo.postgres.config.*

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

import com.youtoo.std.*

object MailApp extends ZIOApp, JsonSupport {
  import com.youtoo.cqrs.Codecs.json.given

  inline val FetchSize = 1_000L

  type Environment =
    FlywayMigration & ZConnectionPool & CQRSPersistence & SnapshotStore & MailEventStore & MailCQRS & Server & Server.Config & NettyConfig & MailService & MailClient & GmailPool & MailRepository & SnapshotStrategy.Factory & Tracing & Baggage & Meter

  given environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  private val config = Server.Config.default
    .port(8181)

  private val nettyConfig = NettyConfig.default
    .leakDetection(NettyConfig.LeakDetectionLevel.DISABLED)

  private val configLayer = ZLayer.succeed(config)
  private val nettyConfigLayer = ZLayer.succeed(nettyConfig)

  private val instrumentationScopeName = "com.youtoo.mail.MailApp"
  private val resourceName = "mail"

  override val bootstrap: ZLayer[Any, Nothing, Environment] =
    Scope.default ++ Log.layer >>> Runtime.disableFlags(
      RuntimeFlag.FiberRoots,
    ) ++ Runtime.enableRuntimeMetrics ++ Runtime.enableAutoBlockingExecutor ++ Runtime.enableFlags(
      RuntimeFlag.EagerShiftBack,
    ) ++
      ZLayer
        .makeSome[Scope, Environment](
          zio.metrics.jvm.DefaultJvmMetrics.live.unit,
          DatabaseConfig.pool,
          // PostgresCQRSPersistence.live(),
          MemoryCQRSPersistence.live(),
          FlywayMigration.live(),
          SnapshotStore.live(),
          MailEventStore.live(),
          MailService.live(),
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

  val routes: Routes[Environment, Response] = Routes(
    Method.POST / "api" / "mail-accounts" -> handler { (req: Request) =>
      endpoint.boundary("add_mail_account", req) {
        for {
          account <- req.body.fromBody[CreateMailAccountRequest]
          accountId <- addMailAccount(account)
        } yield Response.json(s"""{"id":"$accountId"}""")
      }
    },
    Method.GET / "api" / "mail-accounts" / long("accountId") -> handler { (accountId: Long, req: Request) =>
      endpoint.boundary("get_mail_account", req) {
        getMailAccount(MailAccount.Id(Key(accountId))) map {
          case Some(account) => Response.json(account.toJson)
          case None => Response.notFound
        }
      }
    },
    Method.PUT / "api" / "mail-accounts" / long("accountId") / "sync-settings" / "toggle-auto-sync" -> handler {
      (accountId: Long, req: Request) =>
        endpoint.boundary("toggle_mail_account_sync_auto", req) {
          req.body.fromBody[Boolean] flatMap { autoSync =>
            updateSyncConfig(MailAccount.Id(Key(accountId)), autoSync = Some(autoSync)) map { _ => Response.ok }
          }
        }
    },
    Method.PUT / "api" / "mail-accounts" / long("accountId") / "sync-settings" / "auto-sync-schedule" -> handler {
      (accountId: Long, req: Request) =>
        endpoint.boundary("update_mail_account_auto_sync_schedule", req) {
          req.body.fromBody[String] flatMap { schedule =>
            updateSyncConfig(MailAccount.Id(Key(accountId)), schedule = Some(schedule)) map { _ => Response.ok }
          }
        }
    },
    Method.GET / "api" / "mail-data" / long("accountId") -> handler { (accountId: Long, req: Request) =>
      endpoint.boundary("get_mail_data", req) {
        getAllMailData() map { mailData => Response.json(mailData.toJson) }
      }
    },
    Method.GET / "api" / "mail-data" / string("mailId") -> handler { (mailId: String, req: Request) =>
      endpoint.boundary("get_mail_data", req) {
        getMailData(MailData.Id(mailId)) map {
          case Some(data) => Response.json(data.toJson)
          case None => Response.notFound
        }
      }
    },
    Method.GET / "api" / "mail-state" / long("accountId") -> handler { (accountId: Long, req: Request) =>
      endpoint.boundary("get_mail_state", req) {
        getMailState(MailAccount.Id(Key(accountId))) map {
          case Some(state) => Response.json(state.toJson)
          case None => Response.notFound
        }
      }
    },
    Method.GET / "api" / "mail-sync" / long("accountId") -> handler { (accountId: Long, req: Request) =>
      endpoint.boundary("trigger_mail_sync", req) {
        triggerMailSync(MailAccount.Id(Key(accountId))) `as` Response.ok
      }
    },
  )

  def addMailAccount(request: CreateMailAccountRequest): RIO[Environment, Long] =
    for {
      id <- MailAccount.Id.gen
      timestamp <- Timestamp.now

      account = MailAccount(
        id = id,
        name = request.name,
        email = request.email,
        settings = request.settings,
        timestamp = timestamp,
      )

      a <- MailService.save(account)
    } yield a

  def getMailAccount(id: MailAccount.Id): RIO[Environment, Option[MailAccount]] = MailService.loadAccount(id)

  def updateSyncConfig(
    id: MailAccount.Id,
    schedule: Option[String] = None,
    autoSync: Option[Boolean] = None,
  ): RIO[Environment, Unit] = for {
    acc <- MailService.loadAccount(id)

    _ <- acc match {
      case Some(acc) =>
        def go(config: SyncConfig) = (schedule, autoSync) match {
          case (Some(schedule), Some(autoSync)) =>
            config.copy(autoSyncSchedule = SyncConfig.CronExpression(schedule), autoSyncEnabled = autoSync)
          case (_, Some(autoSync)) => config.copy(autoSyncEnabled = autoSync)
          case (Some(schedule), _) => config.copy(autoSyncSchedule = SyncConfig.CronExpression(schedule))
          case (_, _) => config
        }

        val updatedAccount = acc.copy(
          settings = acc.settings.copy(syncConfig = go(acc.settings.syncConfig)),
        )

        MailService.save(updatedAccount)

      case None => ZIO.unit
    }

  } yield ()

  def getAllMailData(): RIO[Environment, Chunk[MailData.Id]] = MailService.loadMails(FetchOptions())

  def getMailData(id: MailData.Id): RIO[Environment, Option[MailData]] = MailService.loadMail(id)

  def getMailState(id: MailAccount.Id): RIO[Environment, Option[Mail]] = MailService.loadState(id)

  def triggerMailSync(id: MailAccount.Id): RIO[Environment, Unit] = ???

  def run: RIO[Environment & Scope, Unit] =
    for {
      _ <- endpoint.uptime

      config <- ZIO.config[DatabaseConfig]
      _ <- FlywayMigration.run(config)

      _ <- Server.serve(routes)
    } yield ()

}

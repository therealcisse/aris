package com.youtoo
package job

import scala.language.future

import zio.*
import zio.jdbc.*

import cats.implicits.*

import com.youtoo.postgres.*
import com.youtoo.cqrs.*
import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.service.*

import com.youtoo.job.model.*
import com.youtoo.job.service.*
import com.youtoo.job.repository.*
import com.youtoo.job.store.*
import com.youtoo.postgres.config.*

import zio.json.*

import zio.http.{Version as _, *}
import zio.http.netty.NettyConfig
import zio.schema.codec.BinaryCodec

import com.youtoo.observability.RestEndpoint
import com.youtoo.observability.RestEndpoint.*
import com.youtoo.observability.otel.OtelSdk

import zio.telemetry.opentelemetry.metrics.*
import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.baggage.Baggage

import com.youtoo.std.utils.*

object JobApp extends ZIOApp, JsonSupport {
  import com.youtoo.cqrs.Codecs.json.given

  inline val FetchSize = 1_000L

  type Environment =
    FlywayMigration & ZConnectionPool & CQRSPersistence & SnapshotStore & JobEventStore & JobCQRS & Server & Server.Config & NettyConfig & JobService & JobRepository & SnapshotStrategy.Factory & Tracing & Baggage & Meter

  given environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  private val config = Server.Config.default
    .port(8181)

  private val nettyConfig = NettyConfig.default
    .leakDetection(NettyConfig.LeakDetectionLevel.DISABLED)

  private val configLayer = ZLayer.succeed(config)
  private val nettyConfigLayer = ZLayer.succeed(nettyConfig)

  private val instrumentationScopeName = "com.youtoo.job.JobApp"
  private val resourceName = "job"

  val bootstrap: ZLayer[Any, Nothing, Environment] =
    Log.layer >>> Runtime.disableFlags(
      RuntimeFlag.FiberRoots,
    ) ++ Runtime.enableRuntimeMetrics ++ Runtime.enableAutoBlockingExecutor ++ Runtime.enableFlags(
      RuntimeFlag.EagerShiftBack,
    ) ++
      ZLayer
        .make[Environment](
          zio.metrics.jvm.DefaultJvmMetrics.live.unit,
          DatabaseConfig.pool,
          com.youtoo.cqrs.service.postgres.PostgresCQRSPersistence.live(),
          // com.youtoo.cqrs.service.memory.MemoryCQRSPersistence.live(),
          FlywayMigration.live(),
          SnapshotStore.live(),
          JobEventStore.live(),
          JobService.live(),
          JobRepository.live(),
          JobCQRS.live(),
          configLayer,
          nettyConfigLayer,
          Server.customized,
          SnapshotStrategy.live(),
          OtelSdk.custom(resourceName),
          OpenTelemetry.tracing(instrumentationScopeName),
          OpenTelemetry.metrics(instrumentationScopeName),
          OpenTelemetry.logging(instrumentationScopeName),
          OpenTelemetry.baggage(),
          // OpenTelemetry.zioMetrics,
          OpenTelemetry.contextZIO,
        )
        .orDie ++ Runtime.setConfigProvider(ConfigProvider.envProvider)

  def loadAll(offset: Option[Key], limit: Long): ZIO[Environment, Throwable, Chunk[Job]] =
    JobService.loadMany(offset, limit)

  def load(key: Key): ZIO[Environment, Throwable, Option[Job]] = JobService.load(Job.Id(key))

  val endpoint = RestEndpoint(RestEndpoint.Service("job"))

  val routes: Routes[Environment, Response] = Routes(
    Method.GET / "jobs" / "health" -> handler(Response.json(ProjectInfo.toJson)),
    Method.GET / "job" -> handler { (req: Request) =>
      endpoint.boundary("get_jobs", req) {
        val offset = req.queryParamTo[Long]("offset").toOption
        val limit = req.queryParamToOrElse[Long]("limit", FetchSize)

        loadAll(offset = offset.map(Key.apply), limit) map { jobs =>
          val nextOffset =
            (if jobs.size < limit then None else jobs.minOption).map(id => s""","nextOffset":"$id"""").getOrElse("")

          Response.json(s"""{"jobs":${jobs.toJson}$nextOffset}""")
        }

      }

    },
    Method.GET / "job" / long("id") -> handler { (id: Long, req: Request) =>
      endpoint.boundary("get_job", req) {
        val key = Key(id)

        load(key) map {
          case Some(job) =>
            val bytes = summon[BinaryCodec[Job]].encode(job)

            Response(
              Status.Ok,
              Headers(Header.ContentType(MediaType.application.json).untyped),
              Body.fromChunk(bytes),
            )

          case None => Response.notFound
        }
      }

    },
    Method.DELETE / "job" / long("id") -> handler { (id: Long, req: Request) =>
      endpoint.boundary("cancel_job", req) {
        val key = Key(id)

        for {
          timestamp <- Timestamp.gen
          _ <- JobService.cancelJob(Job.Id(key), timestamp)
        } yield Response.ok
      }

    },
  )

  def run: RIO[Environment & Scope, Unit] =
    for {
      _ <- endpoint.uptime

      config <- ZIO.config[DatabaseConfig]
      _ <- FlywayMigration.run(config)

      _ <- Server.serve(routes)
    } yield ()

}

package com.youtoo
package ingestion

import scala.language.future

import zio.*
import zio.jdbc.*

import cats.implicits.*

import com.youtoo.postgres.*
import com.youtoo.cqrs.*
import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.service.*

import com.youtoo.ingestion.model.*
import com.youtoo.ingestion.service.*
import com.youtoo.ingestion.repository.*
import com.youtoo.cqrs.service.memory.*
import com.youtoo.ingestion.store.*
import com.youtoo.postgres.config.*

import zio.http.{Version as _, *}
import zio.http.netty.NettyConfig
import zio.schema.codec.BinaryCodec
import java.nio.charset.StandardCharsets

import com.youtoo.observability.RestEndpoint
import com.youtoo.observability.RestEndpoint.*
import com.youtoo.observability.otel.OtelSdk

import zio.telemetry.opentelemetry.metrics.*
import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.baggage.Baggage

import zio.json.*

import com.youtoo.std.utils.*

object IngestionApp extends ZIOApp, JsonSupport {
  import com.youtoo.cqrs.Codecs.json.given

  inline val FetchSize = 1_000L

  type Environment =
    FlywayMigration & ZConnectionPool & CQRSPersistence & SnapshotStore & IngestionEventStore & IngestionCQRS & Server & Server.Config & NettyConfig & IngestionService & IngestionRepository & SnapshotStrategy.Factory & Tracing & Baggage & Meter

  given environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  private val config = Server.Config.default
    .port(8181)

  private val nettyConfig = NettyConfig.default
    .leakDetection(NettyConfig.LeakDetectionLevel.DISABLED)

  private val configLayer = ZLayer.succeed(config)
  private val nettyConfigLayer = ZLayer.succeed(nettyConfig)

  private val instrumentationScopeName = "com.youtoo.ingestion.IngestionApp"
  private val resourceName = "ingestion"

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
          // PostgresCQRSPersistence.live(),
          MemoryCQRSPersistence.live(),
          FlywayMigration.live(),
          SnapshotStore.live(),
          IngestionEventStore.live(),
          IngestionService.live(),
          IngestionRepository.live(),
          IngestionCQRS.live(),
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

  def loadIds(ids: List[Key]): ZIO[Environment, Throwable, List[Ingestion]] =
    ZIO
      .foreachPar(ids) { key =>
        IngestionService.load(Ingestion.Id(key))
      }
      .map(_.mapFilter(identity))

  def loadAll(offset: Option[Key], limit: Long): ZIO[Environment, Throwable, Chunk[Key]] =
    IngestionService.loadMany(offset, limit)

  def load(key: Key): ZIO[Environment, Throwable, Option[Ingestion]] = IngestionService.load(Ingestion.Id(key))

  def addCmd(key: Key, cmd: IngestionCommand): ZIO[Environment, Throwable, Unit] = IngestionCQRS.add(key, cmd)

  def addIngestion(): ZIO[Environment, Throwable, Ingestion.Id] =
    for {
      id <- Ingestion.Id.gen

      timestamp <- Timestamp.gen

      _ <- IngestionCQRS.add(id.asKey, IngestionCommand.StartIngestion(id, timestamp))

      opt <- IngestionService.load(id)

      _ <- opt.fold(ZIO.unit) { ingestion =>
        atomically {
          IngestionService.save(ingestion)
        }
      }

    } yield id

  val endpoint = RestEndpoint(RestEndpoint.Service("ingestion"))

  val routes: Routes[Environment, Response] = Routes(
    Method.GET / "ingestion" / "health" -> handler(Response.json(ProjectInfo.toJson)),
    Method.POST / "dataload" / "ingestion" -> handler { (req: Request) =>
      endpoint.boundary("dataload_ingestions", req) {

        req.body.fromBody[List[Key]] flatMap (ids =>
          for {
            ins <- loadIds(ids)

            bytes = ins
              .map(in => String(summon[BinaryCodec[Ingestion]].encode(in).toArray, StandardCharsets.UTF_8.name))
              .mkString("[", ",", "]")

            resp = Response(
              Status.Ok,
              Headers(Header.ContentType(MediaType.application.json).untyped),
              Body.fromCharSequence(s"""{"ingestions":$bytes}"""),
            )

          } yield resp
        )
      }

    },
    Method.GET / "ingestion" -> handler { (req: Request) =>
      endpoint.boundary("get_ingestions_keys", req) {
        val offset = req.queryParamTo[Long]("offset").toOption
        val limit = req.queryParamToOrElse[Long]("limit", FetchSize)

        atomically {

          loadAll(offset = offset.map(Key.apply), limit) map { ids =>
            val nextOffset =
              (if ids.size < limit then None else ids.minOption).map(id => s""","nextOffset":"$id"""").getOrElse("")

            Response(
              Status.Ok,
              Headers(Header.ContentType(MediaType.application.json).untyped),
              Body.fromCharSequence(s"""{"ids":${ids.toJson}$nextOffset}"""),
            )

          }

        }
      }

    },
    Method.GET / "ingestion" / long("id") -> handler { (id: Long, req: Request) =>
      endpoint.boundary("get_ingestion", req) {
        val key = Key(id)

        load(key) map {
          case Some(ingestion) =>
            val bytes = summon[BinaryCodec[Ingestion]].encode(ingestion)

            Response(
              Status.Ok,
              Headers(Header.ContentType(MediaType.application.json).untyped),
              Body.fromChunk(bytes),
            )

          case None => Response.notFound
        }
      }

    },
    Method.PUT / "ingestion" / long("id") -> handler { (id: Long, req: Request) =>
      endpoint.boundary("add_ingestion_cmd", req) {
        val key = Key(id)

        for {
          cmd <- req.body.fromBody[IngestionCommand]
          _ <- addCmd(key, cmd)

        } yield Response.ok

      }

    },
    Method.GET / "ingestion" / long("id") / "validate" -> handler { (id: Long, req: Request) =>
      endpoint.boundary("validate_ingestion", req) {
        val numFiles = req.queryParamTo[Long]("numFiles")
        val status = req.queryParamToOrElse[String]("status", "initial")

        numFiles match {
          case Left(_) => ZIO.succeed(Response.notFound)
          case Right(n) =>
            load(key = Key(id)) map {
              case None => Response.notFound
              case Some(ingestion) =>
                if ingestion.status.totalFiles.fold(false)(_.size == n) && (ingestion.status is status) then Response.ok
                else Response.notFound
            }

        }
      }

    },
    Method.POST / "ingestion" -> handler { (req: Request) =>
      endpoint.boundary("add_ingestion", req) {
        addIngestion() map (id => Response.json(s"""{"id":"$id"}"""))

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

package com.youtoo
package ingestion

import scala.language.future

import zio.*
import zio.jdbc.*
import zio.logging.*
import zio.logging.backend.*

import zio.metrics.*
import zio.metrics.connectors.prometheus.*
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.metrics.connectors.MetricsConfig
import zio.metrics.connectors.prometheus

import cats.implicits.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.service.*

import com.youtoo.ingestion.model.*
import com.youtoo.ingestion.service.*
import com.youtoo.ingestion.repository.*
import com.youtoo.cqrs.service.postgres.*
import com.youtoo.ingestion.store.*
import com.youtoo.cqrs.config.*

import zio.http.{Version as _, *}
import zio.http.netty.NettyConfig
import zio.schema.codec.BinaryCodec
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object IngestionBenchmarkServer extends ZIOApp {
  import com.youtoo.cqrs.Codecs.json.given

  inline val FetchSize = 1_000L

  type Environment =
    FlywayMigration & ZConnectionPool & CQRSPersistence & SnapshotStore & IngestionEventStore & IngestionCQRS & Server & Server.Config & NettyConfig & IngestionService & IngestionRepository & PrometheusPublisher & MetricsConfig & SnapshotStrategy.Factory

  given environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  private val config = Server.Config.default
    .port(8181)

  private val nettyConfig = NettyConfig.default
    .leakDetection(NettyConfig.LeakDetectionLevel.DISABLED)

  private val configLayer = ZLayer.succeed(config)
  private val nettyConfigLayer = ZLayer.succeed(nettyConfig)

  val bootstrap: ZLayer[Any, Nothing, Environment] =
    Runtime.disableFlags(
      RuntimeFlag.FiberRoots,
    ) ++ Runtime.enableRuntimeMetrics ++ Runtime.enableAutoBlockingExecutor ++ Runtime.enableFlags(
      RuntimeFlag.EagerShiftBack,
    ) ++ Runtime.removeDefaultLoggers >>> SLF4J.slf4j ++
      ZLayer
        .make[Environment](
          DatabaseConfig.pool,
          PostgresCQRSPersistence.live(),
          FlywayMigration.live(),
          SnapshotStore.live(),
          IngestionEventStore.live(),
          IngestionService.live(),
          IngestionRepository.live(),
          IngestionCQRS.live(),
          configLayer,
          nettyConfigLayer,
          Server.customized,
          prometheus.publisherLayer,
          prometheus.prometheusLayer,
          ZLayer.succeed(MetricsConfig(interval = Duration(5L, TimeUnit.SECONDS))),
          SnapshotStrategy.live(),
        )
        .orDie ++ Runtime.setConfigProvider(ConfigProvider.envProvider)

  val routes: Routes[Environment, Response] = Routes(
    Method.GET / "metrics" -> handler(ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))),
    Method.GET / "health" -> handler((_: Request) => Response.ok),
    Method.POST / "dataload" / "ingestion" -> handler { (req: Request) =>
      boundary("POST /dataload/ingestion") {

        req.body.fromBody[List[Key]] flatMap (ids =>
          for {
            ingestions <- ZIO.foreachPar(ids) { key =>
              IngestionService.load(Ingestion.Id(key))
            }

            ins = ingestions.mapFilter(identity)

            bytes = ins
              .map(in => String(summon[BinaryCodec[Ingestion]].encode(in).toArray, StandardCharsets.UTF_8.name))
              .mkString("[", ",", "]")

            resp = Response(
              Status.Ok,
              Headers(Header.ContentType(MediaType.application.json).untyped),
              Body.fromCharSequence(s"""{"ingestions":$bytes}"""),
            )

          } yield resp,
        )
      }

    },
    Method.GET / "ingestion" -> handler { (req: Request) =>
      boundary("GET /ingestion") {
        val offset = req.queryParamTo[Long]("offset").toOption
        val limit = req.queryParamToOrElse[Long]("limit", FetchSize)

        atomically {

          IngestionService.loadMany(offset = offset.map(Key.apply), limit) map { ids =>
            val bytes = String(summon[BinaryCodec[Chunk[Key]]].encode(ids).toArray, StandardCharsets.UTF_8.name)

            val nextOffset =
              (if ids.size < limit then None else ids.minOption).map(id => s""","nextOffset":"$id"""").getOrElse("")

            Response(
              Status.Ok,
              Headers(Header.ContentType(MediaType.application.json).untyped),
              Body.fromCharSequence(s"""{"ids":$bytes$nextOffset}"""),
            )

          }

        }
      }

    },
    Method.GET / "ingestion" / long("id") -> handler { (id: Long, req: Request) =>
      boundary(s"GET /ingestion/$id") {
        val key = Key(id)

        IngestionService.load(Ingestion.Id(key)) map {
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
      boundary(s"GET /ingestion/$id") {
        val key = Key(id)

        req.body.fromBody[IngestionCommand] flatMap (cmd => IngestionCQRS.add(key, cmd) `as` Response.ok)
      }

    },
    Method.GET / "ingestion" / long("id") / "validate" -> handler { (id: Long, req: Request) =>
      boundary(s"GET /ingestion/$id/validate") {
        val numFiles = req.queryParamTo[Long]("numFiles")
        val status = req.queryParamToOrElse[String]("status", "initial")

        numFiles match {
          case Left(_) => ZIO.succeed(Response.notFound)
          case Right(n) =>
            IngestionService.load(id = Ingestion.Id(Key(id))) map {
              case None => Response.notFound
              case Some(ingestion) =>
                if ingestion.status.totalFiles.fold(false)(_.size == n) && (ingestion.status is status) then Response.ok
                else Response.notFound
            }

        }
      }

    },
    Method.POST / "ingestion" -> handler {

      boundary(s"POST /ingestion") {
        for {
          id <- Ingestion.Id.gen

          timestamp <- Timestamp.now

          _ <- IngestionCQRS.add(id.asKey, IngestionCommand.StartIngestion(id, timestamp))

          opt <- IngestionService.load(id)

          _ <- opt.fold(ZIO.unit) { ingestion =>
            atomically {
              IngestionService.save(ingestion)
            }
          }

        } yield Response.json(s"""{"id":"$id"}""")
      }

    },
  ).sandbox

  def run: RIO[Environment, Unit] =
    for {
      config <- ZIO.config[DatabaseConfig]
      _ <- FlywayMigration.run(config)
      _ <- Server.serve(routes)
    } yield ()

}

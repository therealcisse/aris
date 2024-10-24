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

object BenchmarkServer extends ZIOApp {
  import com.youtoo.cqrs.Codecs.json.given

  inline val FetchSize = 1_000L

  val jvmMetricsLayer = zio.metrics.jvm.DefaultJvmMetrics.live

  type Environment =
    FlywayMigration & ZConnectionPool & CQRSPersistence & SnapshotStore & IngestionEventStore & IngestionCQRS & IngestionProvider & IngestionCheckpointer & Server & Server.Config & NettyConfig & IngestionService & IngestionRepository & PrometheusPublisher & MetricsConfig & SnapshotStrategy.Factory

  given environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  private val config = Server.Config.default
    .port(8181)

  private val nettyConfig = NettyConfig.default
    .leakDetection(NettyConfig.LeakDetectionLevel.DISABLED)

  private val configLayer = ZLayer.succeed(config)
  private val nettyConfigLayer = ZLayer.succeed(nettyConfig)

  val bootstrap: ZLayer[Any, Nothing, Environment] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j ++
      ZLayer
        .make[Environment](
          DatabaseConfig.pool,
          PostgresCQRSPersistence.live(),
          FlywayMigration.live(),
          SnapshotStore.live(),
          IngestionProvider.live(),
          IngestionEventStore.live(),
          IngestionCheckpointer.live(),
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
    Method.POST / "dataload" / "ingestion" -> handler { (req: Request) =>
      req.body.fromBody[List[Key]] foldZIO (
        failure = _ => ZIO.succeed(Response.notFound),
        success = ids =>
          for {
            ingestions <- ZIO.foreachPar(ids) { key =>
              IngestionCQRS.load(key)
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

    },
    Method.GET / "ingestion" -> handler { (req: Request) =>
      val offset = req.queryParam("offset").filterNot(_.isEmpty)
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

    },
    Method.GET / "ingestion" / string("id") -> handler { (id: String, req: Request) =>
      val key = Key.wrap(id)

      IngestionCQRS.load(key) map {
        case Some(ingestion) =>
          val bytes = summon[BinaryCodec[Ingestion]].encode(ingestion)

          Response(
            Status.Ok,
            Headers(Header.ContentType(MediaType.application.json).untyped),
            Body.fromChunk(bytes),
          )

        case None => Response.notFound
      }

    },
    Method.PUT / "ingestion" / string("id") -> handler { (id: String, req: Request) =>
      val key = Key.wrap(id)

      req.body.fromBody[IngestionCommand] foldZIO (
        failure = _ => ZIO.succeed(Response.notFound),
        success = cmd => IngestionCQRS.add(key, cmd) `as` Response.ok
      )

    },
    Method.GET / "ingestion" / string("id") / "validate" -> handler { (id: String, req: Request) =>
      val numFiles = req.queryParamTo[Long]("numFiles")
      val status = req.queryParamToOrElse[String]("status", "initial")

      numFiles match {
        case Left(_) => ZIO.succeed(Response.notFound)
        case Right(n) =>
          IngestionCQRS.load(id = Key.wrap(id)) map {
            case None => Response.notFound
            case Some(ingestion) =>
              if ingestion.status.totalFiles.fold(false)(_.size == n) && (ingestion.status is status) then Response.ok
              else Response.notFound
          }

      }

    },
    Method.POST / "ingestion" -> handler {

      for {
        id <- Key.gen

        timestamp <- Timestamp.now

        _ <- IngestionCQRS.add(id, IngestionCommand.StartIngestion(Ingestion.Id(id), timestamp))

        opt <- IngestionCQRS.load(id)

        _ <- opt.fold(ZIO.unit) { ingestion =>
          atomically {
            IngestionService.save(ingestion)
          }
        }

      } yield Response.json(s"""{"id":"$id"}""")

    },
  ).sandbox

  val run: URIO[Environment, ExitCode] =
    (
      for {
        config <- ZIO.config[DatabaseConfig]
        _ <- FlywayMigration.run(config)
        _ <- Server.serve(routes)
      } yield ()
    ).exitCode

}

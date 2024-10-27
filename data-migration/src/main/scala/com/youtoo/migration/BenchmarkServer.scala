package com.youtoo
package migration

import scala.language.future

import zio.*
import zio.jdbc.*
import zio.stream.*
import zio.logging.*
import zio.logging.backend.SLF4J

import zio.metrics.*
import zio.metrics.connectors.prometheus.*
import zio.metrics.connectors.prometheus.PrometheusPublisher
import zio.metrics.connectors.MetricsConfig
import zio.metrics.connectors.prometheus

import cats.implicits.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.service.*

import com.youtoo.migration.model.*
import com.youtoo.migration.service.*
import com.youtoo.migration.repository.*
import com.youtoo.cqrs.service.postgres.*
import com.youtoo.migration.store.*
import com.youtoo.cqrs.config.*

import com.youtoo.std.*

import zio.http.{Version as _, *}
import zio.http.netty.NettyConfig
import zio.schema.codec.BinaryCodec
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object BenchmarkServer extends ZIOApp {
  import com.youtoo.cqrs.Codecs.json.given

  inline val FetchSize = 1_000L

  val logger = Runtime.setConfigProvider(ConfigProvider.envProvider) >>> Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  type Environment =
    FlywayMigration & ZConnectionPool & CQRSPersistence & SnapshotStore & MigrationEventStore & MigrationCQRS & MigrationProvider & MigrationCheckpointer & Server & Server.Config & NettyConfig & MigrationService & MigrationRepository & PrometheusPublisher & MetricsConfig & SnapshotStrategy.Factory & DataMigration & Interrupter & Healthcheck

  given environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  private val config = Server.Config.default
    .port(8181)

  private val nettyConfig = NettyConfig.default
    .leakDetection(NettyConfig.LeakDetectionLevel.DISABLED)

  private val configLayer = ZLayer.succeed(config)
  private val nettyConfigLayer = ZLayer.succeed(nettyConfig)

  val bootstrap: ZLayer[Any, Nothing, Environment] =
    logger ++
      ZLayer
        .make[Environment](
          DatabaseConfig.pool,
          PostgresCQRSPersistence.live(),
          FlywayMigration.live(),
          SnapshotStore.live(),
          MigrationProvider.live(),
          MigrationEventStore.live(),
          MigrationCheckpointer.live(),
          MigrationService.live(),
          MigrationRepository.live(),
          MigrationCQRS.live(),
          configLayer,
          nettyConfigLayer,
          Server.customized,
          prometheus.publisherLayer,
          prometheus.prometheusLayer,
          ZLayer.succeed(MetricsConfig(interval = Duration(5L, TimeUnit.SECONDS))),
          SnapshotStrategy.live(),
          DataMigration.live(),
          Interrupter.live(),
          Healthcheck.live(),
        )
        .orDie

  val routes: Routes[Environment & Scope, Response] = Routes(
    Method.GET / "metrics" -> handler(ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))),
    Method.POST / "dataload" / "migration" -> handler { (req: Request) =>
      req.body.fromBody[List[Key]] foldZIO (
        failure = _ => ZIO.succeed(Response.notFound),
        success = ids =>
          for {
            ingestions <- ZIO.foreachPar(ids) { key =>
              MigrationCQRS.load(key)
            }

            ins = ingestions.mapFilter(identity)

            bytes = ins
              .map(in => String(summon[BinaryCodec[Migration]].encode(in).toArray, StandardCharsets.UTF_8.name))
              .mkString("[", ",", "]")

            resp = Response(
              Status.Ok,
              Headers(Header.ContentType(MediaType.application.json).untyped),
              Body.fromCharSequence(s"""{"ingestions":$bytes}"""),
            )

          } yield resp,
      )

    },
    Method.GET / "migration" -> handler { (req: Request) =>
      val offset = req.queryParam("offset").filterNot(_.isEmpty)
      val limit = req.queryParamToOrElse[Long]("limit", FetchSize)

      atomically {

        MigrationService.loadMany(offset = offset.map(Key.apply), limit) map { ids =>
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
    Method.GET / "migration" / string("id") -> handler { (id: String, req: Request) =>
      val key = Key.wrap(id)

      MigrationCQRS.load(key) map {
        case Some(migration) =>
          val bytes = summon[BinaryCodec[Migration]].encode(migration)

          Response(
            Status.Ok,
            Headers(Header.ContentType(MediaType.application.json).untyped),
            Body.fromChunk(bytes),
          )

        case None => Response.notFound
      }

    },
    Method.POST / "migration" -> handler {

      for {
        id <- Key.gen

        timestamp <- Timestamp.now

        _ <- MigrationCQRS.add(id, MigrationCommand.RegisterMigration(Migration.Id(id), timestamp))

        opt <- MigrationCQRS.load(id)

        _ <- opt.fold(ZIO.unit) { migration =>
          atomically {
            MigrationService.save(migration)
          }
        }

      } yield Response.json(s"""{"id":"$id"}""")

    },
    Method.POST / "migration" / string("id") / "run" -> handler { (id: String, req: Request) =>
      val numKeys = req.queryParamToOrElse[Long]("numKeys", 10L)

      val processor: ZLayer[Any, Nothing, DataMigration.Processor] = ZLayer.succeed {

        new DataMigration.Processor {
          def count(): Task[Long] = ZIO.succeed(numKeys)
          def load(): ZStream[Any, Throwable, Key] = ZStream((0L until numKeys).map(i => Key(s"$i"))*)
          def process(key: Key): Task[Unit] = ZIO.logInfo(s"Processed $key")

        }
      }

      val op =
        DataMigration.run(id = Migration.Id(Key(id))).provideSomeLayer[MigrationCQRS & DataMigration & Scope](processor)

      op `as` Response.ok

    },
    Method.DELETE / "migration" / string("id") / "stop" -> handler { (id: String, req: Request) =>
      Interrupter.interrupt(id = Key(id)) `as` Response.ok

    },
  ).sandbox

  val run: URIO[Environment & Scope, ExitCode] =
    (
      for {
        config <- ZIO.config[DatabaseConfig]
        _ <- FlywayMigration.run(config)
        _ <- Server.serve(routes)
      } yield ()
    ).exitCode

}

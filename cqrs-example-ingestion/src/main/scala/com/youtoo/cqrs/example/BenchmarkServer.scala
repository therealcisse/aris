package com.youtoo.cqrs
package example

import zio.*
import zio.jdbc.*

import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.service.*

import com.youtoo.cqrs.example.model.*
import com.youtoo.cqrs.example.service.*
import com.youtoo.cqrs.example.repository.*
import com.youtoo.cqrs.service.postgres.*
import com.youtoo.cqrs.config.*

import zio.http.{Version as _, *}
import zio.http.netty.NettyConfig
import zio.http.netty.NettyConfig.LeakDetectionLevel
import zio.schema.codec.BinaryCodec
import zio.http.SSLConfig.Data

object BenchmarkServer extends ZIOApp {
  import com.youtoo.cqrs.Codecs.json.given

  type Environment =
    Migration & ZConnectionPool & CQRSPersistence & SnapshotStore & IngestionEventStore & IngestionCQRS & IngestionProvider & IngestionCheckpointer & Server & Server.Config & NettyConfig & IngestionService & IngestionRepository

  given environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  private val config = Server.Config.default
    .port(8181)

  private val nettyConfig = NettyConfig.default
    .leakDetection(LeakDetectionLevel.DISABLED)

  private val configLayer = ZLayer.succeed(config)
  private val nettyConfigLayer = ZLayer.succeed(nettyConfig)

  val bootstrap: ZLayer[Any, Nothing, Environment] =
    ZLayer
      .make[Environment](
        DatabaseConfig.pool,
        PostgresCQRSPersistence.live(),
        Migration.live(),
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
      )
      .orDie ++ Runtime.setConfigProvider(ConfigProvider.envProvider)

  val routes: Routes[Environment, Response] = Routes(
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

        case None => Response.status(Status.NotFound)
      }

    }.sandbox,
    Method.POST / "ingestion" / string("id") -> handler { (id: String, req: Request) =>

      val key = Key.wrap(id)

      for {
        body <- req.body.asChunk

        resp <- summon[BinaryCodec[IngestionCommand]].decode(body) match {
          case Left(_) => ZIO.succeed(Response.status(Status.NotFound))
          case Right(cmd) => IngestionCQRS.add(key, cmd) `as` Response.ok

        }

      } yield resp

    },
    Method.POST / "ingestion" -> handler {

      for {
        id <- Key.gen

        timestamp <- Timestamp.now

        _ <- IngestionCQRS.add(id, IngestionCommand.StartIngestion(Ingestion.Id(id), timestamp))

      } yield Response.json(s"""{"id":$id}""")

    },
  ).sandbox

  val run: URIO[Environment, ExitCode] =
    (
      for {
        config <- ZIO.config[DatabaseConfig]
        _ <- Migration.run(config)
        _ <- Server.serve(routes)
      } yield ()
    ).exitCode

}

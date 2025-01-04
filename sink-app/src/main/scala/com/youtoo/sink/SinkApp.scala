package com.youtoo
package sink

import scala.language.future

import zio.*
import zio.jdbc.*
import zio.prelude.*

import com.youtoo.postgres.*
import com.youtoo.cqrs.*
import com.youtoo.cqrs.store.*
import com.youtoo.cqrs.service.*

import com.youtoo.sink.model.*
import com.youtoo.sink.service.*
import com.youtoo.sink.store.*
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

import com.youtoo.std.utils.*

object SinkApp extends ZIOApp, JsonSupport {
  inline val FetchSize = 1_000L

  object Port extends Newtype[Int] {
    extension (a: Type) def value: Int = unwrap(a)
  }

  given Config[Port.Type] = Config.int.nested("sink_app_port").withDefault(8181).map(Port(_))

  type Environment =
    FlywayMigration & ZConnectionPool & CQRSPersistence & SnapshotStore & SinkEventStore & SinkCQRS & Server & Server.Config & NettyConfig & SinkService & SnapshotStrategy.Factory & Tracing & Baggage & Meter

  given environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  private val nettyConfig = NettyConfig.default
    .leakDetection(NettyConfig.LeakDetectionLevel.DISABLED)

  private val configLayer = ZLayer {
    for {
      port <- ZIO.config[Port.Type]

      config = Server.Config.default.port(port.value)
    } yield config

  }

  private val nettyConfigLayer = ZLayer.succeed(nettyConfig)

  private val instrumentationScopeName = "com.youtoo.sink.SinkApp"
  private val resourceName = "sink"

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
          SinkEventStore.live(),
          SinkService.live(),
          SinkCQRS.live(),
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

  def loadAll(): ZIO[Environment, Throwable, List[SinkDefinition]] =
    SinkService.loadAll()

  def load(key: Key): ZIO[Environment, Throwable, Option[SinkDefinition]] = SinkService.load(SinkDefinition.Id(key))

  val endpoint = RestEndpoint(RestEndpoint.Service("sink"))

  val routes: Routes[Environment, Response] = Routes(
    Method.GET / "sinks" / "health" -> handler(Response.json(ProjectInfo.toJson)),
    Method.GET / "sinks" -> handler { (req: Request) =>
      endpoint.boundary("get_sinks", req) {
        loadAll() map { sinks =>
          Response.json(sinks.toJson)
        }

      }

    },
    Method.GET / "sinks" / long("id") -> handler { (id: Long, req: Request) =>
      endpoint.boundary("get_sink", req) {
        val key = Key(id)

        load(key) map {
          case Some(sink) =>
            Response.json(sink.toJson)

          case None => Response.notFound
        }
      }

    },
    Method.DELETE / "sinks" / long("id") -> handler { (id: Long, req: Request) =>
      endpoint.boundary("delete_sink", req) {
        val key = Key(id)

        SinkService.deleteSink(SinkDefinition.Id(key)) `as` Response.ok
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

package com.youtoo
package lock

import scala.language.future

import zio.*
import zio.http.{Version as _, *}
import zio.http.netty.NettyConfig

import zio.json.*
import zio.prelude.*

import com.youtoo.postgres.config.*

import com.youtoo.observability.RestEndpoint
import com.youtoo.observability.RestEndpoint.*
import com.youtoo.observability.otel.OtelSdk
import com.youtoo.postgres.*

import com.youtoo.lock.*
import com.youtoo.lock.repository.*

import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.metrics.Meter
import zio.telemetry.opentelemetry.baggage.Baggage

import com.youtoo.std.utils.*

object LockApp extends ZIOApp with JsonSupport {
  object Port extends Newtype[Int] {
    extension (a: Type) def value: Int = unwrap(a)
  }

  given Config[Port.Type] = Config.int.nested("lock_app_port").withDefault(8181).map(Port(_))

  type Environment =
    LockManager & LockRepository & Server & Server.Config & Tracing & Baggage & Meter

  given environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  private val configLayer = ZLayer {
    for {
      port <- ZIO.config[Port.Type]

      config = Server.Config.default.port(port.value)
    } yield config

  }

  private val nettyConfig = NettyConfig.default
    .leakDetection(NettyConfig.LeakDetectionLevel.DISABLED)
  private val nettyConfigLayer = ZLayer.succeed(nettyConfig)

  private val instrumentationScopeName = "com.youtoo.lock.LockApp"
  private val resourceName = "lock"

  val bootstrap: ZLayer[Any, Nothing, Environment] =
    Log.layer >>> Runtime.disableFlags(
      RuntimeFlag.FiberRoots,
    ) ++ Runtime.enableRuntimeMetrics ++ Runtime.enableAutoBlockingExecutor ++ Runtime.enableFlags(
      RuntimeFlag.EagerShiftBack,
    ) ++
      ZLayer
        .make[Environment](
          DatabaseConfig.pool,
          // LockRepository.memory(),
          LockRepository.postgres(),
          LockManager.live(),
          configLayer,
          nettyConfigLayer,
          Server.customized,
          OtelSdk.custom(resourceName),
          OpenTelemetry.tracing(instrumentationScopeName),
          OpenTelemetry.metrics(instrumentationScopeName),
          OpenTelemetry.logging(instrumentationScopeName),
          OpenTelemetry.baggage(),
          OpenTelemetry.contextZIO,
        )
        .orDie ++ Runtime.setConfigProvider(ConfigProvider.envProvider)

  val endpoint = RestEndpoint(RestEndpoint.Service("lock"))

  val routes: Routes[Environment, Response] = Routes(
    Method.GET / "locks" / "health" -> handler(Response.json(ProjectInfo.toJson)),
    Method.GET / "locks" -> handler { (req: Request) =>
      endpoint.boundary("get_locks", req) {
        LockManager.locks.map(locks => Response.json(locks.toJson))
      }
    },
  )

  def run: RIO[Environment & Scope, Unit] =
    for {
      _ <- endpoint.uptime
      _ <- Server.serve(routes)
    } yield ()
}

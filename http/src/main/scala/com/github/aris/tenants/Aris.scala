package com.github
package aris
package tenants

import com.github.aris.http.JsonSupport
import com.github.aris.service.CQRSPersistence
import com.github.aris.service.memory.MemoryCQRSPersistence
import com.github.aris.store.SnapshotStore
import com.github.aris.domain.Change
import zio.*
import zio.http.*
import zio.http.netty.*
import zio.json.*
import zio.metrics.jvm.DefaultJvmMetrics
import zio.prelude.*

object Aris extends ZIOApp, JsonSupport {
  object Port extends Newtype[Int] {
    extension (a: Type) def value: Int = unwrap(a)
  }

  given Config[Port.Type] = Config.int.nested("http_port").withDefault(8181).map(Port(_))

  type Environment =
    CQRSPersistence & SnapshotStore & Server & Server.Config & NettyConfig & TenantService & SnapshotStrategy.Factory

  given environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  private val configLayer = ZLayer {
    for {
      port <- ZIO.config[Port.Type]
      config = Server.Config.default.port(port.value)
    } yield config
  }

  private val nettyConfig = NettyConfig.default.leakDetection(NettyConfig.LeakDetectionLevel.DISABLED)
  private val nettyConfigLayer = ZLayer.succeed(nettyConfig)

  val bootstrap: ZLayer[Any, Throwable, Environment] =
    ZLayer.makeSome[Any, Environment](
      DefaultJvmMetrics.live.unit,
      MemoryCQRSPersistence.live(),
      SnapshotStore.live(),
      TenantService.live(),
      SnapshotStrategy.live(),
      configLayer,
      nettyConfigLayer,
      Server.customized,
    )

  final case class TenantInfo(id: Int, name: String, description: String)
  object TenantInfo {
    implicit val codec: JsonCodec[TenantInfo] = DeriveJsonCodec.gen[TenantInfo]
  }

  final case class TenantView(id: Int, name: String, description: String, created: Long, status: String)
  object TenantView {
    implicit val codec: JsonCodec[TenantView] = DeriveJsonCodec.gen[TenantView]
  }

  final case class TenantEventView(
    event: String,
    id: Int,
    name: Option[String],
    description: Option[String],
    timestamp: Long,
  )
  object TenantEventView {
    implicit val codec: JsonCodec[TenantEventView] = DeriveJsonCodec.gen[TenantEventView]
  }

  final case class ChangeView(version: Long, event: TenantEventView)
  object ChangeView {
    implicit val codec: JsonCodec[ChangeView] = DeriveJsonCodec.gen[ChangeView]
  }

  private def toEventView(ev: TenantEvent): TenantEventView =
    ev match {
      case TenantEvent.TenantAdded(id, name, desc, ts) =>
        TenantEventView("TenantAdded", id.value, Some(name), Some(desc), ts.value)
      case TenantEvent.TenantDeleted(id, ts) =>
        TenantEventView("TenantDeleted", id.value, None, None, ts.value)
      case TenantEvent.TenantDisabled(id, ts) =>
        TenantEventView("TenantDisabled", id.value, None, None, ts.value)
      case TenantEvent.TenantEnabled(id, ts) =>
        TenantEventView("TenantEnabled", id.value, None, None, ts.value)
    }

  private def toChangeView(ch: Change[TenantEvent]): ChangeView =
    ChangeView(ch.version.value, toEventView(ch.payload))

  private def toView(t: NameTenant): TenantView =
    TenantView(t.id.value, t.name, t.description, t.created.value, t.status.toString)

  val routes: Routes[Environment, Response] = Routes(
    Method.POST / "tenants" -> handler { (req: Request) =>
      for {
        body <- req.jsonBody[TenantInfo]
        ts   <- Timestamp.gen
        _    <- ZIO.serviceWithZIO[TenantService](_.addTenant(Namespace.wrap(body.id), body.name, body.description, ts))
      } yield Response.ok
    },
    Method.GET / "tenants" / int("id") -> handler { (id: Int, _: Request) =>
      for {
        tenant <- ZIO.serviceWithZIO[TenantService](_.loadTenant(Namespace.wrap(id)))
      } yield tenant.map(toView).toJsonResponse
    },
    Method.GET / "tenants" -> handler { (_: Request) =>
      for {
        tenants <- ZIO.serviceWithZIO[TenantService](_.loadTenants(FetchOptions()))
      } yield tenants.map(toView).toJsonResponse
    },
    Method.DELETE / "tenants" / int("id") -> handler { (id: Int, _: Request) =>
      for {
        ts <- Timestamp.gen
        _  <- ZIO.serviceWithZIO[TenantService](_.deleteTenant(Namespace.wrap(id), ts))
      } yield Response.ok
    },
    Method.POST / "tenants" / int("id") / "enable" -> handler { (id: Int, _: Request) =>
      for {
        ts <- Timestamp.gen
        _  <- ZIO.serviceWithZIO[TenantService](_.enableTenant(Namespace.wrap(id), ts))
      } yield Response.ok
    },
    Method.POST / "tenants" / int("id") / "disable" -> handler { (id: Int, _: Request) =>
      for {
        ts <- Timestamp.gen
        _  <- ZIO.serviceWithZIO[TenantService](_.disableTenant(Namespace.wrap(id), ts))
      } yield Response.ok
    },
    Method.GET / "tenants" / int("id") / "events" -> handler { (id: Int, _: Request) =>
      for {
        eventsOpt <- ZIO.serviceWithZIO[TenantService](_.loadEvents(Namespace.wrap(id)))
        events = eventsOpt.map(_.map(toChangeView).toChunk).getOrElse(Chunk.empty)
      } yield events.toJsonResponse
    }
  )

  def run: RIO[Environment & Scope, Unit] =
    Server.serve(routes).provideSome[Scope](bootstrap)
}

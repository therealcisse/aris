package com.youtoo
package observability

import cats.implicits.*

import io.opentelemetry.api.trace.*

import zio.*
import zio.telemetry.opentelemetry.metrics.*
import zio.telemetry.opentelemetry.common.*
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.baggage.Baggage

import zio.http.*

import java.lang as jl;

import zio.schema.codec.*

object RestEndpoint {

  extension (body: Body)
    inline def fromBody[A: BinaryCodec: Tag]: Task[A] =
      for {
        ch <- body.asChunk
        a <- ZIO.fromEither {
          summon[BinaryCodec[A]].decode(ch)

        }.tapError { e =>
          Log.error(s"Error decoding entity ${Tag[A]}", e)
        }

      } yield a

  inline def boundary[R, E](tag: String, request: Request)(
    body: ZIO[R, E, Response],
  ): URIO[R & Baggage & Tracing & Meter, Response] =
    ZIO.serviceWithZIO[Tracing] { (tracing: Tracing) =>
      val effect = body @@ tracing.aspects.root(
        tag,
        attributes = Attributes(Attribute.string("git_commit_hash", YouToo.gitCommitHash)),
        spanKind = SpanKind.SERVER,
      )

      val op = effect.catchAllCause {
        _.failureOrCause.fold(
          { case e =>
            Log.error(s"[$tag] - Request failed", e) `as` Response.internalServerError

          },
          Exit.failCause,
        )

      }

      val startTime = jl.System.nanoTime()

      for {
        activeCounter <- Metrics.activeRequests
        _ <- activeCounter.inc()

        response <- op.ensuring {
          activeCounter.dec()

        }

        endTime = jl.System.nanoTime()

        attributes = Attributes(
          Attribute.string("http_method", request.method.name),
          Attribute.string("http_path", tag),
          Attribute.long("status_code", response.status.code.toLong),
        )

        latency <- Metrics.requestLatency
        _ <- latency.record((endTime - startTime).toDouble / 1e9, attributes)

        requestCounter <- Metrics.requestCount
        _ <- requestCounter.inc(attributes)

      } yield response

    }

  object Metrics {
    val requestLatency: ZIO[Meter, Nothing, Histogram[Double]] =
      ZIO.serviceWithZIO[Meter] { meter =>
        meter.histogram(
          name = "youtoo_http_request_duration",
          unit = "ms".some,
          description = "Request latency in milliseconds".some,
          boundaries = Chunk.iterate(1.0, 10)(_ + 1.0).some,
        )
      }

    val requestCount: ZIO[Meter, Nothing, Counter[Long]] =
      ZIO.serviceWithZIO[Meter] { meter =>
        meter.counter(
          name = "youtoo_http_requests_total",
          description = "Total number of HTTP requests".some,
        )
      }

    val activeRequests: ZIO[Meter, Nothing, UpDownCounter[Long]] =
      ZIO.serviceWithZIO[Meter] { meter =>
        meter.upDownCounter("youtoo_http_active_requests", description = "Number of active HTTP requests".some)
      }

    val uptime: ZIO[Meter & Scope, Throwable, Unit] =
      ZIO.serviceWithZIO[Meter] { meter =>
        meter.observableGauge(
          "youtoo_application_uptime_seconds",
          description = "The uptime of the application in seconds".some,
        ) { guage =>
          val uptimeInSeconds = (jl.System.currentTimeMillis() / 1000.0) - startEpochSeconds
          guage.record(uptimeInSeconds, Attributes.empty)
        }
      }

    val startEpochSeconds: Long = jl.System.currentTimeMillis() / 1000L

  }

}

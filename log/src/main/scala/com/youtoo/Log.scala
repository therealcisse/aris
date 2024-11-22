package com.youtoo

import zio.{Cause, Runtime, URIO, ZIO, ZIOAspect}
import zio.logging.*
import zio.logging.backend.*

import zio.telemetry.opentelemetry.tracing.Tracing

object Log {

  inline def layer = Runtime.removeDefaultLoggers >>> SLF4J.slf4j >>> logMetrics

  val GitCommitHashAnnotation: ZIOAspect[Nothing, Any, Nothing, Any, Nothing, Any] = LogAnnotation[String](
    name = "Version",
    combine = (_, r) => r,
    render = identity,
  ).apply(ProjectInfo.versionSha)

  val TraceId: LogAnnotation[String] = LogAnnotation[String](
    name = "TraceId",
    combine = (_, r) => r,
    render = identity,
  )

  val SpanId: LogAnnotation[String] = LogAnnotation[String](
    name = "SpanId",
    combine = (_, r) => r,
    render = identity,
  )

  inline def info(message: => String): URIO[Tracing, Unit] =
    for {
      tracing <- ZIO.service[Tracing]
      context <- tracing.getCurrentSpanContextUnsafe
      _ <- ZIO.logInfo(message) @@ GitCommitHashAnnotation @@ TraceId(context.getTraceId) @@ SpanId(context.getSpanId)
    } yield ()

  inline def debug(message: => String): URIO[Tracing, Unit] =
    for {
      tracing <- ZIO.service[Tracing]
      context <- tracing.getCurrentSpanContextUnsafe
      _ <- ZIO.logInfo(message) @@ GitCommitHashAnnotation @@ TraceId(context.getTraceId) @@ SpanId(context.getSpanId)
    } yield ()

  inline def debug[E](message: => String, cause: => E): URIO[Tracing, Unit] =
    for {
      tracing <- ZIO.service[Tracing]
      context <- tracing.getCurrentSpanContextUnsafe
      _ <- ZIO.logDebugCause(message, Cause.fail(cause)) @@ GitCommitHashAnnotation @@ TraceId(
        context.getTraceId,
      ) @@ SpanId(context.getSpanId)
    } yield ()

  inline def error(message: => String): URIO[Tracing, Unit] =
    for {
      tracing <- ZIO.service[Tracing]
      context <- tracing.getCurrentSpanContextUnsafe
      _ <- ZIO.logError(message) @@ GitCommitHashAnnotation @@ TraceId(context.getTraceId) @@ SpanId(context.getSpanId)
    } yield ()

  inline def error[E](message: => String, cause: => E): URIO[Tracing, Unit] =
    for {
      tracing <- ZIO.service[Tracing]
      context <- tracing.getCurrentSpanContextUnsafe
      _ <- ZIO.logErrorCause(message, Cause.fail(cause)) @@ GitCommitHashAnnotation @@ TraceId(
        context.getTraceId,
      ) @@ SpanId(context.getSpanId)
    } yield ()

}

package com.youtoo
package observability
package otel

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import io.opentelemetry.semconv.ResourceAttributes
import zio.*
import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter
import io.opentelemetry.sdk.trace.`export`.BatchSpanProcessor
import java.util.concurrent.TimeUnit

object TracerProvider {

  /**
   * Prints to stdout in OTLP Json format
   */
  def stdout(resourceName: String): RIO[Scope, SdkTracerProvider] =
    for {
      spanExporter <- ZIO.fromAutoCloseable(ZIO.succeed(OtlpJsonLoggingSpanExporter.create()))
      spanProcessor <- ZIO.fromAutoCloseable(ZIO.succeed(SimpleSpanProcessor.create(spanExporter)))
      tracerProvider <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            SdkTracerProvider
              .builder()
              .setResource(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, resourceName)))
              .addSpanProcessor(spanProcessor)
              .build(),
          ),
        )
    } yield tracerProvider

  def otlp(resourceName: String): RIO[Scope, SdkTracerProvider] =
    for {
      spanExporter <- ZIO.fromAutoCloseable(
        ZIO.succeed(
          OtlpGrpcSpanExporter
            .builder()
            .setTimeout(2, TimeUnit.SECONDS)
            .build(),
        ),
      )
      spanProcessor <- ZIO.fromAutoCloseable(
        ZIO.succeed(
          BatchSpanProcessor
            .builder(spanExporter)
            .setScheduleDelay(100, TimeUnit.MILLISECONDS)
            .build(),
        ),
      )
      tracerProvider <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            SdkTracerProvider
              .builder()
              .setResource(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, resourceName)))
              .addSpanProcessor(spanProcessor)
              .build(),
          ),
        )
    } yield tracerProvider

}

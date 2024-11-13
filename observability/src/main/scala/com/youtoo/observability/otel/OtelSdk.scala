package com.youtoo
package observability
package otel

import zio.*
import zio.telemetry.opentelemetry.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.api

object OtelSdk {

  def custom(resourceName: String): TaskLayer[api.OpenTelemetry] =
    OpenTelemetry.custom(
      for {
        tracerProvider <- TracerProvider.jaeger(resourceName)
        meterProvider <- MeterProvider.prometheus(resourceName)
        loggerProvider <- LoggerProvider.seq(resourceName)
        openTelemetry <- ZIO.fromAutoCloseable(
          ZIO.succeed(
            OpenTelemetrySdk
              .builder()
              .setTracerProvider(tracerProvider)
              .setMeterProvider(meterProvider)
              .setLoggerProvider(loggerProvider)
              .build,
          ),
        )
      } yield openTelemetry,
    )

}

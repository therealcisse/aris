package com.youtoo
package observability
package otel

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.`export`.SimpleLogRecordProcessor
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.semconv.ResourceAttributes
import zio.*
import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingLogRecordExporter

object LoggerProvider {

  /**
   * Prints to stdout in OTLP Json format
   */
  def stdout(resourceName: String): RIO[Scope, SdkLoggerProvider] =
    for {
      logRecordExporter <- ZIO.fromAutoCloseable(ZIO.succeed(OtlpJsonLoggingLogRecordExporter.create()))
      logRecordProcessor <- ZIO.fromAutoCloseable(ZIO.succeed(SimpleLogRecordProcessor.create(logRecordExporter)))
      loggerProvider <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            SdkLoggerProvider
              .builder()
              .setResource(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, resourceName)))
              .addLogRecordProcessor(logRecordProcessor)
              .build(),
          ),
        )
    } yield loggerProvider

  /**
   * https://datalust.co/seq
   */
  def seq(resourceName: String): RIO[Scope, SdkLoggerProvider] =
    for {
      endpoint <- ZIO.config[LoggingConfig.Endpoint]
      logRecordExporter <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            OtlpHttpLogRecordExporter
              .builder()
              .setEndpoint(endpoint.value)
              .build(),
          ),
        )
      logRecordProcessor <- ZIO.fromAutoCloseable(ZIO.succeed(SimpleLogRecordProcessor.create(logRecordExporter)))
      loggerProvider <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            SdkLoggerProvider
              .builder()
              .setResource(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, resourceName)))
              .addLogRecordProcessor(logRecordProcessor)
              .build(),
          ),
        )
    } yield loggerProvider

  /**
   * https://fluentbit.io/
   */
  def fluentbit(resourceName: String): RIO[Scope, SdkLoggerProvider] =
    for {
      logRecordExporter <- ZIO.fromAutoCloseable(ZIO.succeed(OtlpHttpLogRecordExporter.builder().build()))
      logRecordProcessor <- ZIO.fromAutoCloseable(ZIO.succeed(SimpleLogRecordProcessor.create(logRecordExporter)))
      loggerProvider <-
        ZIO.fromAutoCloseable(
          ZIO.succeed(
            SdkLoggerProvider
              .builder()
              .setResource(Resource.create(Attributes.of(ResourceAttributes.SERVICE_NAME, resourceName)))
              .addLogRecordProcessor(logRecordProcessor)
              .build(),
          ),
        )
    } yield loggerProvider

}

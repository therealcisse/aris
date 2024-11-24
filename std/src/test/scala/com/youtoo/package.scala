package com.youtoo

import zio.*

import zio.telemetry.opentelemetry.tracing.*
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.`export`.SimpleSpanProcessor
import io.opentelemetry.api.trace.*
import zio.telemetry.opentelemetry.context.ContextStorage
import io.opentelemetry.sdk.trace.SdkTracerProvider

val inMemoryTracer: UIO[(InMemorySpanExporter, Tracer)] = for {
  spanExporter <- ZIO.succeed(InMemorySpanExporter.create())
  spanProcessor <- ZIO.succeed(SimpleSpanProcessor.create(spanExporter))
  tracerProvider <- ZIO.succeed(SdkTracerProvider.builder().addSpanProcessor(spanProcessor).build())
  tracer = tracerProvider.get("TracingTest")
} yield (spanExporter, tracer)

val inMemoryTracerLayer: ULayer[InMemorySpanExporter & Tracer] =
  ZLayer.fromZIOEnvironment(inMemoryTracer.map { case (inMemorySpanExporter, tracer) =>
    ZEnvironment(inMemorySpanExporter).add(tracer)
  })

def tracingMockLayer(
  logAnnotated: Boolean = false,
): URLayer[ContextStorage, Tracing] =
  inMemoryTracerLayer >>> Tracing.live(logAnnotated)

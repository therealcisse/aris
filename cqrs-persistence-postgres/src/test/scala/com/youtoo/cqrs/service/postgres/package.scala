package com.youtoo
package cqrs
package service
package postgres

import zio.*
import zio.jdbc.*

import java.sql.*

extension (sql: SqlFragment)
  inline def getExecutionPlan: ZIO[ZConnection & Scope, Throwable, String] =
    ZIO.serviceWithZIO[ZConnection](_.accessZIO { connection =>
      ZIO.attemptBlocking {

        def getSql(sql: SqlFragment) =
          val sb: StringBuilder = StringBuilder()

          @scala.annotation.nowarn
          def go(segments: Chunk[SqlFragment.Segment]): Unit =
            sql.segments.foreach {
              case SqlFragment.Segment.Empty => ()
              case syntax: SqlFragment.Segment.Syntax => sb.append(syntax.value)
              case param: SqlFragment.Segment.Param =>
                val placeholder = param.value match {
                  case iterable: Iterable[?] => Seq.fill(iterable.size)("?").mkString(", ")
                  case _ => "?"
                }

                sb.append(placeholder)

              case nested: SqlFragment.Segment.Nested => go(nested.sql.segments)
            }

          go(sql.segments)

          sb.result()

        def setParams(sql: SqlFragment, statement: PreparedStatement) =
          var paramIndex = 1

          @scala.annotation.nowarn
          def go(segments: Chunk[SqlFragment.Segment]): Unit =
            sql.segments.foreach {
              case SqlFragment.Segment.Empty => ()
              case _: SqlFragment.Segment.Syntax => ()
              case param: SqlFragment.Segment.Param =>
                param.setter.setValue(statement, paramIndex, param.value)
                paramIndex += (param.value match {
                  case iterable: Iterable[?] => iterable.size
                  case _ => 1
                })

              case nested: SqlFragment.Segment.Nested => go(nested.sql.segments)
            }

          go(sql.segments)

        val query = getSql(sql)

        val stmt = connection.prepareStatement(s"EXPLAIN ANALYZE $query")

        setParams(sql, stmt)

        val rs = stmt.executeQuery()

        val sb = StringBuilder()
        while (rs.next())
          sb.append(rs.getString(1)).append("\n")
        rs.close()
        stmt.close()
        sb.toString()
      }

    })

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

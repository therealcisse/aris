package com.youtoo
package cqrs
package service
package postgres

import zio.test.*

import zio.*
import zio.jdbc.*
import zio.prelude.*

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

val keyGen: Gen[Any, Key] = Gen.long.map(Key.apply)

val genNamespace: Gen[Any, Namespace] = Gen.int.map(Namespace.apply)

val genEventProperty: Gen[Any, EventProperty] =
  (Gen.alphaNumericStringBounded(4, 32) <*> Gen.alphaNumericString).map(EventProperty.apply)

val genNamespaceList: Gen[Any, NonEmptyList[Namespace]] =
  Gen.listOfBounded(1, 5)(genNamespace).map {
    case head :: tail => NonEmptyList(head, tail*)
    case Nil => throw IllegalStateException("Should not generate empty lists")
  }

val genHierarchy: Gen[Any, Hierarchy] =
  keyGen.flatMap { key =>
    Gen.oneOf(
      Gen.const(Hierarchy.Child(key)),
      Gen.const(Hierarchy.GrandChild(key)),
      (keyGen <*> keyGen).map { case (gk, pk) => Hierarchy.Descendant(gk, pk) },
    )
  }

val genReference: Gen[Any, Reference] =
  keyGen.map(Reference.apply)

val genCondition: Gen[Any, PersistenceQuery.Condition] =
  for {
    namespaceOpt <- Gen.option(genNamespace)
    propsOpt <- Gen.listOfBounded(0, 16)(genEventProperty).map(NonEmptyList.fromIterableOption)
    hierarchy <- Gen.option(genHierarchy)
    reference <- Gen.option(genReference)
  } yield PersistenceQuery.condition(namespaceOpt, propsOpt, hierarchy, reference)

val genPersistenceQueryAny: Gen[Any, PersistenceQuery] =
  for {
    condition <- genCondition
    moreConditions <- Gen.listOf(genCondition)
  } yield PersistenceQuery.any(condition, moreConditions*)

val genPersistenceQueryForall: Gen[Any, PersistenceQuery] =
  for {
    condition <- Gen.oneOf(
      genCondition,
      genPersistenceQueryAny,
    )

    moreConditions <- Gen.listOf(
      Gen.oneOf(
        genCondition,
        genPersistenceQueryAny,
      ),
    )
  } yield PersistenceQuery.forall(condition, moreConditions*)

val genPersistenceQuery: Gen[Any, PersistenceQuery] =
  Gen.oneOf(
    genPersistenceQueryAny,
    genPersistenceQueryForall,
    genCondition,
  )

val genFetchOptions: Gen[Any, FetchOptions] =
  for {
    offset <- Gen.option(keyGen)
    limit <- Gen.option(Gen.long)
  } yield FetchOptions(offset, limit)

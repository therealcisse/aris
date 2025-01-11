package com.youtoo
package source
package service

import zio.telemetry.opentelemetry.tracing.*

import zio.*
import zio.jdbc.*
import zio.test.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.prelude.*
import zio.mock.*

import com.youtoo.postgres.*
import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*

import com.youtoo.source.model.*
import com.youtoo.source.store.*

object SourceServiceSpec extends MockSpecDefault, TestSupport {

  def spec = suite("SourceServiceSpec")(
    test("addSource calls MockSourceCQRS") {
      check(sourceIdGen, sourceTypeGen) { (id, info) =>
        val expectedCommand = SourceCommand.AddOrModify(id, info)
        val mockEnv = MockSourceCQRS.Add(equalTo(id.asKey -> expectedCommand), result = unit)

        (for {
          _ <- SourceService.addSource(id, info)
        } yield assertCompletes).provideSomeLayer[ZConnectionPool & Tracing](
          ZLayer.makeSome[ZConnectionPool & Tracing, SourceService](
            SourceEventStoreMock.empty,
            SourceService.live(),
            mockEnv,
          ),
        )
      }
    },
    test("deleteSource calls MockSourceCQRS") {
      check(sourceIdGen) { (id) =>
        val expectedCommand = SourceCommand.Delete(id)
        val mockEnv = MockSourceCQRS.Add(equalTo(id.asKey -> expectedCommand), result = unit)

        (for {
          _ <- SourceService.deleteSource(id)
        } yield assertCompletes).provideSomeLayer[ZConnectionPool & Tracing](
          ZLayer.makeSome[ZConnectionPool & Tracing, SourceService](
            SourceEventStoreMock.empty,
            SourceService.live(),
            mockEnv,
          ),
        )
      }
    },
    test("load(id) calls SourceEventStoreMock") {
      check(
        sourceIdGen,
        Gen.listOf(sourceEventChangeGen),
      ) {
        (
          id,
          es,
        ) =>
          val events = NonEmptyList.fromIterableOption(es)

          val mockEnv = SourceEventStoreMock.ReadEventsByQueryAggregate(
            equalTo(
              (
                id.asKey,
                PersistenceQuery.anyNamespace(SourceEvent.NS.Added, SourceEvent.NS.Deleted),
                FetchOptions(),
              ),
            ),
            value(events),
          )

          val handler = SourceEvent.LoadSource()
          val expected = events.fold(None)(handler.applyEvents)

          (for {
            result <- SourceService.load(id)
          } yield assert(result)(equalTo(expected))).provideSomeLayer[ZConnectionPool & Tracing](
            ZLayer.makeSome[ZConnectionPool & Tracing, SourceService](
              MockSourceCQRS.empty,
              SourceService.live(),
              mockEnv,
            ),
          )
      }
    },
    test("loadAll() calls SourceEventStoreMock") {
      check(
        Gen.listOf(sourceEventChangeGen),
      ) {
        (
          es
        ) =>
          val events = NonEmptyList.fromIterableOption(es)

          val mockEnv = SourceEventStoreMock.ReadEventsByQuery(
            equalTo(
              (
                PersistenceQuery.anyNamespace(SourceEvent.NS.Added, SourceEvent.NS.Deleted),
                FetchOptions(),
              ),
            ),
            value(events),
          )

          val handler = SourceEvent.LoadSources()
          val expected = events.fold(Nil)(es => handler.applyEvents(es).values.toList)

          (for {
            sources <- SourceService.loadAll()
          } yield assert(sources)(equalTo(expected))).provideSomeLayer[ZConnectionPool & Tracing](
            ZLayer.makeSome[ZConnectionPool & Tracing, SourceService](
              MockSourceCQRS.empty,
              SourceService.live(),
              mockEnv,
            ),
          )

      }
    },
  ).provideSomeLayerShared(
    ZLayer.make[Tracing & ZConnectionPool](
      ZConnectionMock.pool(),
      tracingMockLayer(),
      zio.telemetry.opentelemetry.OpenTelemetry.contextZIO,
    ),
  ) @@ TestAspect.withLiveClock

}

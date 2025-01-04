package com.youtoo
package sink
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

import com.youtoo.sink.model.*
import com.youtoo.sink.store.*

object SinkServiceSpec extends MockSpecDefault, TestSupport {

  def spec = suite("SinkServiceSpec")(
    test("addSink calls MockSinkCQRS") {
      check(sinkIdGen, sinkTypeGen) { (id, info) =>
        val expectedCommand = SinkCommand.AddOrModify(id, info)
        val mockEnv = MockSinkCQRS.Add(equalTo(id.asKey -> expectedCommand), result = unit)

        (for {
          _ <- SinkService.addSink(id, info)
        } yield assertCompletes).provideSomeLayer[ZConnectionPool & Tracing](
          ZLayer.makeSome[ZConnectionPool & Tracing, SinkService](
            SinkEventStoreMock.empty,
            SinkService.live(),
            mockEnv,
          ),
        )
      }
    },
    test("deleteSink calls MockSinkCQRS") {
      check(sinkIdGen) { (id) =>
        val expectedCommand = SinkCommand.Delete(id)
        val mockEnv = MockSinkCQRS.Add(equalTo(id.asKey -> expectedCommand), result = unit)

        (for {
          _ <- SinkService.deleteSink(id)
        } yield assertCompletes).provideSomeLayer[ZConnectionPool & Tracing](
          ZLayer.makeSome[ZConnectionPool & Tracing, SinkService](
            SinkEventStoreMock.empty,
            SinkService.live(),
            mockEnv,
          ),
        )
      }
    },
    test("load(id) calls SinkEventStoreMock") {
      check(
        sinkIdGen,
        Gen.listOf(sinkEventChangeGen),
      ) {
        (
          id,
          es,
        ) =>
          val events = NonEmptyList.fromIterableOption(es)

          val mockEnv = SinkEventStoreMock.ReadEventsByQueryAggregate(
            equalTo(
              (
                id.asKey,
                PersistenceQuery.anyNamespace(SinkEvent.NS.Added, SinkEvent.NS.Deleted),
                FetchOptions(),
              ),
            ),
            value(events),
          )

          val handler = SinkEvent.LoadSink()
          val expected = events.fold(None)(handler.applyEvents)

          (for {
            result <- SinkService.load(id)
          } yield assert(result)(equalTo(expected))).provideSomeLayer[ZConnectionPool & Tracing](
            ZLayer.makeSome[ZConnectionPool & Tracing, SinkService](
              MockSinkCQRS.empty,
              SinkService.live(),
              mockEnv,
            ),
          )
      }
    },
    test("loadAll() calls SinkEventStoreMock") {
      check(
        Gen.listOf(sinkEventChangeGen),
      ) {
        (
          es
        ) =>
          val events = NonEmptyList.fromIterableOption(es)

          val mockEnv = SinkEventStoreMock.ReadEventsByQuery(
            equalTo(
              (
                PersistenceQuery.anyNamespace(SinkEvent.NS.Added, SinkEvent.NS.Deleted),
                FetchOptions(),
              ),
            ),
            value(events),
          )

          val handler = SinkEvent.LoadSinks()
          val expected = events.fold(Nil)(es => handler.applyEvents(es).values.toList)

          (for {
            sinks <- SinkService.loadAll()
          } yield assert(sinks)(equalTo(expected))).provideSomeLayer[ZConnectionPool & Tracing](
            ZLayer.makeSome[ZConnectionPool & Tracing, SinkService](
              MockSinkCQRS.empty,
              SinkService.live(),
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

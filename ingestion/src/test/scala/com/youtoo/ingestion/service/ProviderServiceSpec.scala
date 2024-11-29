package com.youtoo
package ingestion
package service

import zio.test.*
import zio.prelude.*
import zio.mock.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*
import zio.jdbc.*

import com.youtoo.cqrs.service.*
import com.youtoo.cqrs.service.postgres.*

import com.youtoo.ingestion.model.*
import com.youtoo.cqrs.Codecs.given
import com.youtoo.cqrs.*
import com.youtoo.ingestion.store.*
import com.youtoo.cqrs.domain.*

object ProviderServiceSpec extends MockSpecDefault {
  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    Log.layer >>> testEnvironment

  def spec = suite("ProviderServiceSpec")(
    test("addProvider should save events to EventStore") {
      check(providerIdGen, providerNameGen, providerLocationGen) { (id, name, location) =>
        val expectedEvent = FileEvent.ProviderAdded(id, name, location)

        val eventStoreMock = MockFileEventStore.Save(
          isPayload(id.asKey, expectedEvent),
          value(1L),
        )

        val effect = ProviderService.addProvider(id, name, location)

        assertZIO(
          effect.provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
            eventStoreMock.toLayer >>> ProviderService.live(),
          ),
        )(isUnit)
      }
    },
    test("load should return Provider when found") {
      check(providerIdGen, providerNameGen, providerLocationGen, versionGen) { (id, name, location, version) =>
        val expectedProvider = Provider(id, name, location)
        val expectedEvent = FileEvent.ProviderAdded(id, name, location)
        val expectedChange = Change(version = version, payload = expectedEvent)
        val events = NonEmptyList(expectedChange)

        val eventStoreMock = MockFileEventStore.ReadEventsById(
          equalTo(id.asKey),
          value(Some(events)),
        )

        val effect = ProviderService.load(id)

        assertZIO(
          effect.provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
            eventStoreMock.toLayer >>> ProviderService.live(),
          ),
        )(
          isSome(equalTo(expectedProvider)),
        )
      }
    },
    test("loadAll should return a list of Providers when found") {
      check(providerIdGen, providerNameGen, providerLocationGen, versionGen) { (id, name, location, version) =>
        val expectedProvider = Provider(id, name, location)
        val expectedEvent = FileEvent.ProviderAdded(id, name, location)
        val expectedChange = Change(version = version, payload = expectedEvent)
        val events = NonEmptyList(expectedChange)

        val eventStoreMock = MockFileEventStore.ReadEventsByFilters(
          equalTo((Some(NonEmptyList(Namespace(1))), None, None)),
          value(Some(events)),
        )

        val effect = ProviderService.loadAll(None, 100L)

        assertZIO(
          effect.provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
            eventStoreMock.toLayer >>> ProviderService.live(),
          ),
        )(
          equalTo(List(expectedProvider)),
        )
      }
    },
    test("getFiles should return NonEmptyList[IngestionFile] when files are found") {
      check(
        providerIdGen,
        fileIdGen,
        fileNameGen,
        fileSigGen,
        versionGen,
        fileMetadataGen,
      ) { (providerId, fileId, fileName, sig, version, metadata) =>
        val expectedFile = IngestionFile(fileId, fileName, metadata, sig)
        val expectedEvent = FileEvent.FileAdded(providerId, fileId, fileName, metadata, sig)
        val expectedChange = Change(version = version, payload = expectedEvent)
        val events = NonEmptyList(expectedChange)

        val eventStoreMock = MockFileEventStore.ReadEventsByFilters(
          equalTo((Some(NonEmptyList(Namespace(0))), Some(Hierarchy.Child(providerId.asKey)), None)),
          value(Some(events)),
        )

        val effect = ProviderService.getFiles(providerId, None, 100L)

        assertZIO(
          effect.provideSomeLayer[ZConnectionPool & zio.telemetry.opentelemetry.tracing.Tracing](
            eventStoreMock.toLayer >>> ProviderService.live(),
          ),
        )(
          isSome(equalTo(NonEmptyList(expectedFile))),
        )
      }
    },
  ).provideLayerShared(
    ZConnectionMock.pool() ++ (zio.telemetry.opentelemetry.OpenTelemetry.contextZIO >>> tracingMockLayer()),
  )
}

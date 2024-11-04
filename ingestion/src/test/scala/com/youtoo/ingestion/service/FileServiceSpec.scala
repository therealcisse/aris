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

object FileServiceSpec extends MockSpecDefault {

  def spec = suite("FileServiceSpec")(
    test("addFile should save events to EventStore") {
      check(
        providerIdGen,
        fileIdGen,
        fileNameGen,
        fileMetadataGen,
        fileSigGen,
      ) { (providerId, fileId, fileName, metadata, sig) =>
        val expectedEvent = FileEvent.FileAdded(providerId, fileId, fileName, metadata, sig)

        val eventStoreMock = MockFileEventStore.Save(
          isPayload(fileId.asKey, expectedEvent),
          value(1L),
        )

        val effect = FileService.addFile(providerId, fileId, fileName, metadata, sig)

        assertZIO(effect.provideSomeLayer[ZConnectionPool](eventStoreMock.toLayer >>> FileService.live()))(isUnit)
      }
    },
    test("loadNamed should return IngestionFile when found") {
      check(fileNameGen, versionGen, fileMetadataGen) { (fileName, version, metadata) =>
        // Set up the EventHandler to return the expected IngestionFile
        val expectedFile = IngestionFile(
          id = IngestionFile.Id(0L),
          name = fileName,
          metadata = metadata,
          sig = IngestionFile.Sig("sig"),
        )

        val expectedEvent = FileEvent.FileAdded(
          provider = Provider.Id(1L),
          id = IngestionFile.Id(0L),
          name = fileName,
          metadata = metadata,
          sig = IngestionFile.Sig("sig"),
        )
        val expectedChange = Change(version = version, payload = expectedEvent)

        // Mocking readEvents
        val eventStoreMock = MockFileEventStore.ReadEventsByFilters(
          equalTo((Some(NonEmptyList(Namespace(0))), None, Some(NonEmptyList(EventProperty("name", fileName.value))))),
          value(Some(NonEmptyList(expectedChange))),
        )

        // Test effect
        val effect = FileService.loadNamed(fileName)

        // Run the test
        assertZIO(effect.provideSomeLayer[ZConnectionPool](eventStoreMock.toLayer >>> FileService.live()))(
          isSome(equalTo(expectedFile)),
        )
      }
    },
    test("loadSig should return IngestionFile when found") {
      check(fileSigGen, versionGen, fileMetadataGen) { (fileSig, version, metadata) =>
        // Set up the EventHandler to return the expected IngestionFile
        val expectedFile = IngestionFile(
          id = IngestionFile.Id(0L),
          name = IngestionFile.Name("file-name"),
          metadata = metadata,
          sig = fileSig,
        )

        val expectedEvent = FileEvent.FileAdded(
          provider = Provider.Id(1L),
          id = IngestionFile.Id(0L),
          name = IngestionFile.Name("file-name"),
          metadata = metadata,
          sig = fileSig,
        )
        val expectedChange = Change(version = version, payload = expectedEvent)

        // Mocking readEvents
        val eventStoreMock = MockFileEventStore.ReadEventsByFilters(
          equalTo((Some(NonEmptyList(Namespace(0))), None, Some(NonEmptyList(EventProperty("sig", fileSig.value))))),
          value(Some(NonEmptyList(expectedChange))),
        )

        // Test effect
        val effect = FileService.loadSig(fileSig)

        // Run the test
        assertZIO(effect.provideSomeLayer[ZConnectionPool](eventStoreMock.toLayer >>> FileService.live()))(
          isSome(equalTo(expectedFile)),
        )
      }
    },
    test("load should return IngestionFile when found") {
      check(fileIdGen, versionGen, fileMetadataGen) { (fileId, version, metadata) =>
        // Set up the EventHandler to return the expected IngestionFile
        val expectedFile = IngestionFile(
          id = fileId,
          name = IngestionFile.Name("file-name"),
          metadata = metadata,
          sig = IngestionFile.Sig("sig"),
        )

        val expectedEvent = FileEvent.FileAdded(
          provider = Provider.Id(1L),
          id = fileId,
          name = IngestionFile.Name("file-name"),
          metadata = metadata,
          sig = IngestionFile.Sig("sig"),
        )
        val expectedChange = Change(version = version, payload = expectedEvent)

        // Mocking readEvents
        val eventStoreMock = MockFileEventStore.ReadEventsById(
          equalTo(fileId.asKey),
          value(Some(NonEmptyList(expectedChange))),
        )

        // Test effect
        val effect = FileService.load(fileId)

        // Run the test
        assertZIO(effect.provideSomeLayer[ZConnectionPool](eventStoreMock.toLayer >>> FileService.live()))(
          isSome(equalTo(expectedFile)),
        )
      }
    },
  ).provideLayerShared(ZConnectionMock.pool()) @@ TestAspect.withLiveClock
}

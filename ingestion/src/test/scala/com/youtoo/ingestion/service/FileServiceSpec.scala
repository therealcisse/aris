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
        ingestionFileIdGen,
        ingestionFileNameGen,
        ingestionFileMetadataGen,
        ingestionFileSigGen,
      ) { (providerId, fileId, fileName, metadata, sig) =>
        val expectedEvent = FileEvent.FileAdded(providerId, fileId, fileName, metadata, sig)

        val eventStoreMock = MockFileEventStore.Save(
          isArg(fileId.asKey, expectedEvent),
          value(1L),
        )

        inline def isArg(key: Key, payload: FileEvent) = assertion[(Key, Change[FileEvent])]("FileService.isArg") {
          case (id, ch) => id == key && ch.payload == payload
        }

        val effect = for {
          _ <- FileService.addFile(providerId, fileId, fileName, metadata, sig)
        } yield ()

        assertZIO(effect.provideSomeLayer[ZConnectionPool](eventStoreMock.toLayer >>> FileService.live()))(isUnit)
      }
    },
    test("loadNamed should return IngestionFile when found") {
      check(ingestionFileNameGen, versionGen) { (fileName, version) =>
        // Set up the EventHandler to return the expected IngestionFile
        val expectedFile = IngestionFile(
          id = IngestionFile.Id("file-id"),
          name = fileName,
          metadata = IngestionFile.Metadata(),
          sig = IngestionFile.Sig("sig"),
        )

        val expectedEvent = FileEvent.FileAdded(
          provider = Provider.Id("provider-1"),
          id = IngestionFile.Id("file-id"),
          name = fileName,
          metadata = IngestionFile.Metadata(),
          sig = IngestionFile.Sig("sig"),
        )
        val expectedChange = Change(version = version, payload = expectedEvent)

        // Mocking readEvents
        val eventStoreMock = MockFileEventStore.ReadEventsByFilters(
          equalTo((Some(NonEmptyList(Namespace(0))), None, Some(NonEmptyList(EventProperty("name", fileName.value))))),
          value(Some(NonEmptyList(expectedChange))),
        )

        // Test effect
        val effect = for {
          result <- FileService.loadNamed(fileName)
        } yield result

        // Run the test
        assertZIO(effect.provideSomeLayer[ZConnectionPool](eventStoreMock.toLayer >>> FileService.live()))(
          isSome(equalTo(expectedFile)),
        )
      }
    },
    test("loadSig should return IngestionFile when found") {
      check(fileSigGen, versionGen) { (fileSig, version) =>
        // Set up the EventHandler to return the expected IngestionFile
        val expectedFile = IngestionFile(
          id = IngestionFile.Id("file-id"),
          name = IngestionFile.Name("file-name"),
          metadata = IngestionFile.Metadata(),
          sig = fileSig,
        )

        val expectedEvent = FileEvent.FileAdded(
          provider = Provider.Id("provider-1"),
          id = IngestionFile.Id("file-id"),
          name = IngestionFile.Name("file-name"),
          metadata = IngestionFile.Metadata(),
          sig = fileSig,
        )
        val expectedChange = Change(version = version, payload = expectedEvent)

        // Mocking readEvents
        val eventStoreMock = MockFileEventStore.ReadEventsByFilters(
          equalTo((Some(NonEmptyList(Namespace(0))), None, Some(NonEmptyList(EventProperty("sig", fileSig.value))))),
          value(Some(NonEmptyList(expectedChange))),
        )

        // Test effect
        val effect = for {
          result <- FileService.loadSig(fileSig)
        } yield result

        // Run the test
        assertZIO(effect.provideSomeLayer[ZConnectionPool](eventStoreMock.toLayer >>> FileService.live()))(
          isSome(equalTo(expectedFile)),
        )
      }
    },
    test("load should return IngestionFile when found") {
      check(fileIdGen, versionGen) { (fileId, version) =>
        // Set up the EventHandler to return the expected IngestionFile
        val expectedFile = IngestionFile(
          id = fileId,
          name = IngestionFile.Name("file-name"),
          metadata = IngestionFile.Metadata(),
          sig = IngestionFile.Sig("sig"),
        )

        val expectedEvent = FileEvent.FileAdded(
          provider = Provider.Id("provider-1"),
          id = fileId,
          name = IngestionFile.Name("file-name"),
          metadata = IngestionFile.Metadata(),
          sig = IngestionFile.Sig("sig"),
        )
        val expectedChange = Change(version = version, payload = expectedEvent)

        // Mocking readEvents
        val eventStoreMock = MockFileEventStore.ReadEventsById(
          equalTo(fileId.asKey),
          value(Some(NonEmptyList(expectedChange))),
        )

        // Test effect
        val effect = for {
          result <- FileService.load(fileId)
        } yield result

        // Run the test
        assertZIO(effect.provideSomeLayer[ZConnectionPool](eventStoreMock.toLayer >>> FileService.live()))(
          isSome(equalTo(expectedFile)),
        )
      }
    },
  ).provideLayerShared(ZConnectionMock.pool()) @@ TestAspect.withLiveClock
}

package com.youtoo
package ingestion
package model

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*
import zio.*
import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.Codecs.given

object LoadFilesSpec extends ZIOSpecDefault {

  def spec = suite("LoadFilesSpec")(
    test("should load all IngestionFiles for a provider from events") {
      check(providerIdGen, Gen.listOfN(5)(ingestionFileGen), versionGen, ingestionFileMetadataGen) {
        (providerId, files, version, metadata) =>
          val handler = new FileEvent.LoadFiles(providerId)

          val events = NonEmptyList
            .fromIterableOption(
              files.map { file =>
                Change(
                  version,
                  FileEvent.FileAdded(
                    provider = providerId,
                    id = file.id,
                    name = file.name,
                    metadata = file.metadata,
                    sig = file.sig,
                  ),
                )
              }.toList ++ List(
                // Add some files from other providers
                Change(
                  version,
                  FileEvent.FileAdded(
                    provider = Provider.Id("other-provider"),
                    id = IngestionFile.Id("other-file"),
                    name = IngestionFile.Name("other-name"),
                    metadata = metadata,
                    sig = IngestionFile.Sig("other-sig"),
                  ),
                ),
              ),
            )
            .getOrElse(
              NonEmptyList(
                Change(
                  version,
                  FileEvent.FileAdded(
                    provider = providerId,
                    id = IngestionFile.Id("dummy"),
                    name = IngestionFile.Name("dummy"),
                    metadata = metadata,
                    sig = IngestionFile.Sig("dummy"),
                  ),
                ),
              ),
            )

          val result = handler.applyEvents(events)

          val expectedFiles = NonEmptyList.fromIterableOption(files).map(_.reverse)

          assert(result)(equalTo(expectedFiles))
      }
    },
    test("should return None if no files for provider in events") {
      check(versionGen, ingestionFileMetadataGen) { case (version, metadata) =>
        val handler = new FileEvent.LoadFiles(Provider.Id("provider-1"))

        val events = NonEmptyList(
          Change(
            version,
            FileEvent.FileAdded(
              provider = Provider.Id("other-provider"),
              id = IngestionFile.Id("file-1"),
              name = IngestionFile.Name("file-name"),
              metadata = metadata,
              sig = IngestionFile.Sig("signature"),
            ),
          ),
        )

        val result = handler.applyEvents(events)

        assert(result)(isNone)
      }
    },
  )
}

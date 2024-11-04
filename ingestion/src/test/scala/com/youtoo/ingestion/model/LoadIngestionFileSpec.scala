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

object LoadIngestionFileSpec extends ZIOSpecDefault {

  def spec = suite("LoadIngestionFileSpec")(
    test("should load IngestionFile by ID from events") {
      check(versionGen, ingestionFileMetadataGen) { (version, metadata) =>
        val fileId = IngestionFile.Id("file-1")
        val handler = new FileEvent.LoadIngestionFile(fileId)

        val events = NonEmptyList(
          Change(
            version,
            FileEvent.FileAdded(
              provider = Provider.Id("provider-1"),
              id = fileId,
              name = IngestionFile.Name("file-name"),
              metadata = metadata,
              sig = IngestionFile.Sig("signature"),
            ),
          ),
        )

        val result = handler.applyEvents(events)

        val expectedFile = IngestionFile(
          id = fileId,
          name = IngestionFile.Name("file-name"),
          metadata = metadata,
          sig = IngestionFile.Sig("signature"),
        )

        assert(result)(isSome(equalTo(expectedFile)))
      }
    },
    test("should return None if file with given ID is not in events") {
      check(versionGen, ingestionFileMetadataGen) { (version, metadata) =>
        val fileId = IngestionFile.Id("non-existent-file")
        val handler = new FileEvent.LoadIngestionFile(fileId)

        val events = NonEmptyList(
          Change(
            version,
            FileEvent.FileAdded(
              provider = Provider.Id("provider-1"),
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

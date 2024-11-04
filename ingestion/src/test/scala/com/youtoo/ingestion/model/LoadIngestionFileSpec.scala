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
      check(versionGen, fileMetadataGen, providerIdGen, fileIdGen, fileNameGen, fileSigGen) {
        (version, metadata, providerId, fileId, fileName, fileSig) =>
          val handler = new FileEvent.LoadIngestionFile(fileId)

          val events = NonEmptyList(
            Change(
              version,
              FileEvent.FileAdded(
                provider = providerId,
                id = fileId,
                name = fileName,
                metadata = metadata,
                sig = fileSig,
              ),
            ),
          )

          val result = handler.applyEvents(events)

          val expectedFile = IngestionFile(
            id = fileId,
            name = fileName,
            metadata = metadata,
            sig = fileSig,
          )

          assert(result)(isSome(equalTo(expectedFile)))
      }
    },
    test("should return None if file with given ID is not in events") {
      check(fileIdGen, versionGen, fileMetadataGen, providerIdGen, fileIdGen, fileNameGen, fileSigGen) {
        (id, version, metadata, providerId, fileId, fileName, fileSig) =>
          val handler = new FileEvent.LoadIngestionFile(id)

          val events = NonEmptyList(
            Change(
              version,
              FileEvent.FileAdded(
                provider = providerId,
                id = fileId,
                name = fileName,
                metadata = metadata,
                sig = fileSig,
              ),
            ),
          )

          val result = handler.applyEvents(events)

          val expectedFile = IngestionFile(
            id = fileId,
            name = fileName,
            metadata = metadata,
            sig = fileSig,
          )

          assert(result)(if id == fileId then isSome(equalTo(expectedFile)) else isNone)
      }
    },
  )
}

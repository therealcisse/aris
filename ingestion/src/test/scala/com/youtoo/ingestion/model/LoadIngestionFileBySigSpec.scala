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

object LoadIngestionFileBySigSpec extends ZIOSpecDefault {
  def spec = suite("LoadIngestionFileBySigSpec")(
    test("should load IngestionFile by signature from events") {
      check(providerIdGen, fileIdGen, fileNameGen, fileSigGen, versionGen, fileMetadataGen) {
        (providerId, fileId, fileName, fileSig, version, metadata) =>
          val handler = new FileEvent.LoadIngestionFileBySig(fileSig)

          val events = NonEmptyList(
            Change(
              version = version,
              payload = FileEvent.FileAdded(
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
    test("should return None if file with given signature is not in events") {
      check(providerIdGen, fileIdGen, fileNameGen, fileSigGen, fileSigGen, versionGen, fileMetadataGen) {
        (providerId, fileId, fileName, fileSig, differentFileSig, version, metadata) =>
          val handler = new FileEvent.LoadIngestionFileBySig(fileSig)

          val events = NonEmptyList(
            Change(
              version,
              FileEvent.FileAdded(
                provider = providerId,
                id = fileId,
                name = fileName,
                metadata = metadata,
                sig = differentFileSig,
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

          assert(result)(if fileSig == differentFileSig then isSome(equalTo(expectedFile)) else isNone)
      }
    },
  )

}

package com.youtoo
package ingestion
package model

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*
import com.youtoo.cqrs.*

object FileCommandHandlerSpec extends ZIOSpecDefault {

  def spec = suite("FileCommandHandlerSpec")(
    test("should produce FileAdded event when AddFile command is applied") {
      // Prepare the command
      val cmd = FileCommand.AddFile(
        provider = Provider.Id("provider-1"),
        id = IngestionFile.Id("file-1"),
        name = IngestionFile.Name("file-name"),
        metadata = IngestionFile.Metadata.File(0L, Timestamp(0L)),
        sig = IngestionFile.Sig("signature"),
      )

      // Apply the command using the CmdHandler
      val events = CmdHandler.applyCmd(cmd)

      // Expected event
      val expectedEvent = FileEvent.FileAdded(
        provider = Provider.Id("provider-1"),
        id = IngestionFile.Id("file-1"),
        name = IngestionFile.Name("file-name"),
        metadata = IngestionFile.Metadata.File(0L, Timestamp(0L)),
        sig = IngestionFile.Sig("signature"),
      )

      // Assert that the events match
      assert(events)(equalTo(NonEmptyList(expectedEvent)))
    },
    test("should produce ProviderAdded event when AddProvider command is applied") {
      // Prepare the command
      val cmd = FileCommand.AddProvider(
        id = Provider.Id("provider-1"),
        name = Provider.Name("provider-name"),
        location = Provider.Location.File("provider-location"),
      )

      // Apply the command using the CmdHandler
      val events = CmdHandler.applyCmd(cmd)

      // Expected event
      val expectedEvent = FileEvent.ProviderAdded(
        id = Provider.Id("provider-1"),
        name = Provider.Name("provider-name"),
        location = Provider.Location.File("provider-location"),
      )

      // Assert that the events match
      assert(events)(equalTo(NonEmptyList(expectedEvent)))
    },
  ) @@ TestAspect.withLiveClock
}

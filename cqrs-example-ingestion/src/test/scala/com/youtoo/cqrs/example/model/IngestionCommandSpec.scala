package com.youtoo.cqrs
package example
package model

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*

object IngestionCommandHandlerSpec extends ZIOSpecDefault {
  val handler = summon[IngestionCommandHandler]

  def spec = suite("IngestionCommandHandlerSpec")(
    test("StartIngestion command produces IngestionStarted event") {
      (Ingestion.Id.gen <&> Timestamp.now) map { (id, timestamp) =>
        val command = IngestionCommand.StartIngestion(id, timestamp)
        val events = handler.applyCmd(command)
        val expectedEvent = IngestionEvent.IngestionStarted(
          command.asInstanceOf[IngestionCommand.StartIngestion].id,
          command.asInstanceOf[IngestionCommand.StartIngestion].timestamp,
        )
        assert(events)(equalTo(NonEmptyList(expectedEvent)))

      }

    },
    test("SetFiles command produces IngestionFilesResolved event") {
      val files = NonEmptySet("file1", "file2")
      val command = IngestionCommand.SetFiles(files)
      val events = handler.applyCmd(command)
      val expectedEvent = IngestionEvent.IngestionFilesResolved(files)
      assert(events)(equalTo(NonEmptyList(expectedEvent)))
    },
    test("Applying the same command multiple times produces the same event") {
      val command = IngestionCommand.FileProcessed("file1")
      val events1 = handler.applyCmd(command)
      val events2 = handler.applyCmd(command)
      assert(events1)(equalTo(events2))
    },
    test("FileProcessed command produces IngestionFileProcessed event") {
      val file = "file1"
      val command = IngestionCommand.FileProcessed(file)
      val events = handler.applyCmd(command)
      val expectedEvent = IngestionEvent.IngestionFileProcessed(file)
      assert(events)(equalTo(NonEmptyList(expectedEvent)))
    },
    test("FileProcessing command produces IngestionFileProcessing event") {
      val file = "file1"
      val command = IngestionCommand.FileProcessing(file)
      val events = handler.applyCmd(command)
      val expectedEvent = IngestionEvent.IngestionFileProcessing(file)
      assert(events)(equalTo(NonEmptyList(expectedEvent)))
    },
    test("FileFailed command produces IngestionFileFailed event") {
      val file = "file2"
      val command = IngestionCommand.FileFailed(file)
      val events = handler.applyCmd(command)
      val expectedEvent = IngestionEvent.IngestionFileFailed(file)
      assert(events)(equalTo(NonEmptyList(expectedEvent)))
    },
    test("StopIngestion command produces IngestionCompleted event") {
      Timestamp.now map { timestamp =>

        val command = IngestionCommand.StopIngestion(timestamp)
        val events = handler.applyCmd(command)
        val expectedEvent = IngestionEvent.IngestionCompleted(timestamp)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }

    },
  )
}

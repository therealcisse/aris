package com.youtoo
package ingestion
package model

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*

object IngestionCommandHandlerSpec extends ZIOSpecDefault {
  val handler = summon[IngestionCommandHandler]

  def spec = suite("IngestionCommandHandlerSpec")(
    test("StartIngestion command produces IngestionStarted event") {
      check(ingestionIdGen, timestampGen) { (id, timestamp) =>
        val command = IngestionCommand.StartIngestion(id, timestamp)
        val events = handler.applyCmd(command)
        val expectedEvent = IngestionEvent.IngestionStarted(id, timestamp)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("SetFiles command produces IngestionFilesResolved event") {
      check(setFilesGen) { command =>
        val events = handler.applyCmd(command)
        val expectedEvent = IngestionEvent.IngestionFilesResolved(command.asInstanceOf[IngestionCommand.SetFiles].files)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("Applying the same command multiple times produces the same event") {
      check(fileIdGen) { fileId =>
        val command = IngestionCommand.FileProcessed(fileId)
        val events1 = handler.applyCmd(command)
        val events2 = handler.applyCmd(command)
        assert(events1)(equalTo(events2))
      }
    },
    test("FileProcessed command produces IngestionFileProcessed event") {
      check(fileIdGen) { file =>
        val command = IngestionCommand.FileProcessed(file)
        val events = handler.applyCmd(command)
        val expectedEvent = IngestionEvent.IngestionFileProcessed(file)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("FileProcessing command produces IngestionFileProcessing event") {
      check(fileIdGen) { file =>
        val command = IngestionCommand.FileProcessing(file)
        val events = handler.applyCmd(command)
        val expectedEvent = IngestionEvent.IngestionFileProcessing(file)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("FileFailed command produces IngestionFileFailed event") {
      check(fileIdGen) { file =>
        val command = IngestionCommand.FileFailed(file)
        val events = handler.applyCmd(command)
        val expectedEvent = IngestionEvent.IngestionFileFailed(file)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("StopIngestion command produces IngestionCompleted event") {
      check(timestampGen) { timestamp =>
        val command = IngestionCommand.StopIngestion(timestamp)
        val events = handler.applyCmd(command)
        val expectedEvent = IngestionEvent.IngestionCompleted(timestamp)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
  ) @@ TestAspect.withLiveClock
}

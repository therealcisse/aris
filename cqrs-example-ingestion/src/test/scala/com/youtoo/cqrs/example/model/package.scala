package com.youtoo.cqrs
package example
package model

import zio.*
import zio.test.*
import zio.prelude.*

import com.youtoo.cqrs.domain.*

import com.youtoo.cqrs.Codecs.given

given ingestionIdGen: Gen[Any, Ingestion.Id] = Gen.fromZIO(Ingestion.Id.gen.orDie)
given timestampGen: Gen[Any, Timestamp]      = Gen.fromZIO(Timestamp.now.orDie)

val startIngestionGen: Gen[Any, IngestionCommand.StartIngestion] =
  for {
    id        <- ingestionIdGen
    timestamp <- timestampGen
  } yield IngestionCommand.StartIngestion(id, timestamp)

val setFilesGen: Gen[Any, IngestionCommand.SetFiles] =
  Gen
    .setOfBounded(1, 12)(Gen.alphaNumericString)
    .map(s =>
      NonEmptySet.fromIterableOption(s) match {
        case None      => throw IllegalArgumentException("empty")
        case Some(nes) => IngestionCommand.SetFiles(nes)
      }
    )

val fileProcessedGen: Gen[Any, IngestionCommand.FileProcessed] =
  Gen.alphaNumericString.map(IngestionCommand.FileProcessed.apply)

val fileFailedGen: Gen[Any, IngestionCommand.FileFailed] =
  Gen.alphaNumericString.map(IngestionCommand.FileFailed.apply)

val stopIngestionGen: Gen[Any, IngestionCommand.StopIngestion] =
  timestampGen.map(IngestionCommand.StopIngestion.apply)

val ingestionCommandGen: Gen[Any, IngestionCommand] =
  Gen.oneOf(
    startIngestionGen,
    setFilesGen,
    fileProcessedGen,
    fileFailedGen,
    stopIngestionGen
  )

given versionGen: Gen[Any, Version] = Gen.fromZIO(Version.gen.orDie)

val ingestionStartedGen: Gen[Any, IngestionEvent.IngestionStarted] =
  for {
    id        <- ingestionIdGen
    timestamp <- timestampGen
  } yield IngestionEvent.IngestionStarted(id, timestamp)

val ingestionFilesResolvedGen: Gen[Any, IngestionEvent.IngestionFilesResolved] =
  Gen
    .setOfBounded(3, 8)(Gen.alphaNumericString)
    .map(s =>
      NonEmptySet.fromIterableOption(s) match {
        case None      => throw IllegalArgumentException("empty")
        case Some(nes) => IngestionEvent.IngestionFilesResolved(nes)
      }
    )

val ingestionFileProcessedGen: Gen[Any, IngestionEvent.IngestionFileProcessed] =
  Gen.alphaNumericString.map(IngestionEvent.IngestionFileProcessed.apply)

val ingestionFileFailedGen: Gen[Any, IngestionEvent.IngestionFileFailed] =
  Gen.alphaNumericString.map(IngestionEvent.IngestionFileFailed.apply)

val ingestionCompletedGen: Gen[Any, IngestionEvent.IngestionCompleted] =
  timestampGen.map(IngestionEvent.IngestionCompleted.apply)

val ingestionEventGen: Gen[Any, IngestionEvent] =
  Gen.oneOf(
    ingestionStartedGen,
    ingestionFilesResolvedGen,
    ingestionFileProcessedGen,
    ingestionFileFailedGen,
    ingestionCompletedGen
  )

def isValidState(status: Ingestion.Status): Boolean = status match {
  case Ingestion.Status.Completed(_) => true
  case Ingestion.Status.Failed(_, _) => true
  case Ingestion.Status.Processing(remaining, processing, processed, failed) =>
    remaining.union(processed).union(failed).union(processing).nonEmpty
  case Ingestion.Status.Resolved(_) => true
  case Ingestion.Status.Initial() =>
    true
  case Ingestion.Status.Stopped(processing, _) =>
    isValidState(processing)
}

val commandSequenceGen: Gen[Any, List[IngestionCommand]] =
  Gen.listOf(ingestionCommandGen)

val eventSequenceGen: Gen[Any, NonEmptyList[Change[IngestionEvent]]] =
  for {
    events <- Gen.listOf(ingestionEventGen)
    if events.nonEmpty
    changes <- Gen.fromZIO {
                 ZIO.foreach(events) { case event =>
                   for {
                     v <- Version.gen.orDie
                   } yield Change(v, event)
                 }
               }
  } yield NonEmptyList.fromIterable(changes.head, changes.tail)

val validEventSequenceGen: Gen[Any, NonEmptyList[Change[IngestionEvent]]] =
  for {
    id        <- ingestionIdGen
    version   <- versionGen
    timestamp <- timestampGen
    startEvent = Change(version, IngestionEvent.IngestionStarted(id, timestamp))
    otherEvents <- Gen.listOf(
                     Gen.oneOf(
                       ingestionFilesResolvedGen,
                       ingestionFileProcessedGen,
                       ingestionFileFailedGen,
                       ingestionCompletedGen
                     )
                   )
    changes <- Gen.fromZIO {

                 ZIO.foreach(otherEvents) { case event =>
                   for {
                     v <- Version.gen.orDie
                   } yield Change(v, event)
                 }
               }
  } yield NonEmptyList.fromIterable(startEvent, changes)

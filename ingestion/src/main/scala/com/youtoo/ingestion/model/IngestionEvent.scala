package com.youtoo
package ingestion
package model

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*

import cats.implicits.*

import zio.*
import zio.prelude.*
import zio.schema.*

enum IngestionEvent {
  case IngestionStarted(id: Ingestion.Id, timestamp: Timestamp)
  case IngestionFilesResolved(files: NonEmptySet[IngestionFile.Id])
  case IngestionFileProcessing(file: IngestionFile.Id)
  case IngestionFileProcessed(file: IngestionFile.Id)
  case IngestionFileFailed(file: IngestionFile.Id)
  case IngestionCompleted(timestamp: Timestamp)
}

type IngestionEventHandler = EventHandler[IngestionEvent, Ingestion]

object IngestionEvent {
  val discriminator: Discriminator = Discriminator("Ingestion")

  given Schema[IngestionEvent] = DeriveSchema.gen

  object NS {
    val IngestionStarted = Namespace(0)
    val IngestionFilesResolved = Namespace(100)
    val IngestionFileProcessing = Namespace(200)
    val IngestionFileProcessed = Namespace(300)
    val IngestionFileFailed = Namespace(400)
    val IngestionCompleted = Namespace(500)
  }

  given MetaInfo[IngestionEvent]  {

    extension (self: IngestionEvent)
      def namespace: Namespace = self match {
        case IngestionEvent.IngestionStarted(_, _) => NS.IngestionStarted
        case IngestionEvent.IngestionFilesResolved(_) => NS.IngestionFilesResolved
        case IngestionEvent.IngestionFileProcessing(_) => NS.IngestionFileProcessing
        case IngestionEvent.IngestionFileProcessed(_) => NS.IngestionFileProcessed
        case IngestionEvent.IngestionFileFailed(_) => NS.IngestionFileFailed
        case IngestionEvent.IngestionCompleted(_) => NS.IngestionCompleted
      }

    extension (self: IngestionEvent) def hierarchy: Option[Hierarchy] = None
    extension (self: IngestionEvent) def props: Chunk[EventProperty] = Chunk.empty
    extension (self: IngestionEvent) def reference: Option[Reference] = None
  }

  given IngestionEventHandler  {

    def applyEvents(events: NonEmptyList[Change[IngestionEvent]]): Ingestion =
      events match {
        case NonEmptyList.Single(ch) =>
          ch.payload match {
            case IngestionEvent.IngestionStarted(id, timestamp) =>
              Ingestion(id = id, status = Ingestion.Status.empty, timestamp = timestamp)

            case _ => throw IllegalArgumentException("Unexpected event, current state is empty")
          }

        case NonEmptyList.Cons(ch, ls) =>
          ch.payload match {
            case IngestionEvent.IngestionStarted(id, timestamp) =>
              applyEvents(zero = Ingestion(id = id, status = Ingestion.Status.empty, timestamp = timestamp), ls)

            case _ => throw IllegalArgumentException("Unexpected event, current state is empty")
          }

      }

    def applyEvents(zero: Ingestion, events: NonEmptyList[Change[IngestionEvent]]): Ingestion =
      events.foldLeft(zero) { (state, event) =>
        val status = event.payload match {
          case IngestionEvent.IngestionStarted(_, _) =>
            throw IllegalArgumentException(s"Unexpected event, current state is ${event.payload.getClass.getName}")

          case IngestionEvent.IngestionFilesResolved(files) =>
            Ingestion.Status.Resolved(files)

          case IngestionEvent.IngestionFileProcessing(file) =>
            state.status match {
              case Ingestion.Status.Processing(remaining, processing, processed, failed) if remaining contains file =>
                Ingestion.Status.Processing(remaining - file, processing + file, processed, failed)

              case Ingestion.Status.Resolved(files) if files contains file =>
                Ingestion.Status.Processing(files - file, Set() + file, Set(), Set())

              case _ => state.status
            }

          case IngestionEvent.IngestionFileProcessed(file) =>
            state.status match {
              case Ingestion.Status.Processing(remaining, processing, processed, failed) if processing contains file =>
                Ingestion.Status.Processing(remaining, processing - file, processed + file, failed)

              case _ => state.status
            }

          case IngestionEvent.IngestionFileFailed(file) =>
            state.status match {
              case Ingestion.Status.Processing(remaining, processing, processed, failed) if processing contains file =>
                Ingestion.Status.Processing(remaining, processing - file, processed, failed + file)

              case _ => state.status
            }

          case IngestionEvent.IngestionCompleted(timestamp) =>
            state.status match {
              case processing @ Ingestion.Status.Processing(_, _, _, _) =>
                Ingestion.Status.Stopped(processing, timestamp)

              case _ => state.status
            }

        }

        state.copy(status = status.isSuccessful)
      }

  }

}

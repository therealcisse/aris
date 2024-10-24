package com.youtoo.cqrs
package ingestion
package model

import com.youtoo.cqrs.domain.*

import cats.implicits.*

import zio.prelude.*
import zio.schema.*

enum IngestionEvent {
  case IngestionStarted(id: Ingestion.Id, timestamp: Timestamp)
  case IngestionFilesResolved(files: NonEmptySet[String])
  case IngestionFileProcessing(file: String)
  case IngestionFileProcessed(file: String)
  case IngestionFileFailed(file: String)
  case IngestionCompleted(timestamp: Timestamp)
}

type IngestionEventHandler = EventHandler[IngestionEvent, Ingestion]

object IngestionEvent {
  val discriminator: Discriminator = Discriminator("Ingestion")

  given Schema[IngestionEvent] = DeriveSchema.gen

  given MetaInfo[IngestionEvent] with {
    extension (self: IngestionEvent)
      def namespace: Namespace = self match {
        case IngestionEvent.IngestionStarted(_, _) => Namespace(0)
        case IngestionEvent.IngestionFilesResolved(_) => Namespace(1)
        case IngestionEvent.IngestionFileProcessing(_) => Namespace(2)
        case IngestionEvent.IngestionFileProcessed(_) => Namespace(3)
        case IngestionEvent.IngestionFileFailed(_) => Namespace(4)
        case IngestionEvent.IngestionCompleted(_) => Namespace(5)

      }
  }

  given IngestionEventHandler with {

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

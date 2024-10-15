package com.youtoo.cqrs
package example
package model

import com.youtoo.cqrs.domain.*

import cats.implicits.*

import zio.prelude.*
import zio.schema.*

enum IngestionEvent {
  case IngestionStarted(id: Ingestion.Id, timestamp: Timestamp)
  case IngestionFilesResolved(files: Set[String])
  case IngestionFileProcessed(file: String)
  case IngestionFileFailed(file: String)
  case IngestionCompleted()

}

type IngestionEventHandler = EventHandler[IngestionEvent, Ingestion]

object IngestionEvent {
  val discriminator: Discriminator = Discriminator("Ingestion")

  given Schema[IngestionEvent] = DeriveSchema.gen

  given IngestionEventHandler with {

    def applyEvents(events: NonEmptyList[Change[IngestionEvent]]): Ingestion =
      events match {
        case NonEmptyList.Single(ch) =>
          ch.payload match {
            case IngestionEvent.IngestionStarted(id, timestamp) =>
              Ingestion(id = id, status = Ingestion.Status.empty, timestamp = timestamp)

            case _ => throw IllegalArgumentException("Unexpected event")
          }

        case NonEmptyList.Cons(ch, ls) =>
          ch.payload match {
            case IngestionEvent.IngestionStarted(id, timestamp) =>
              applyEvents(zero = Ingestion(id = id, status = Ingestion.Status.empty, timestamp = timestamp), ls)

            case _ => throw IllegalArgumentException("Unexpected event")
          }

      }

    def applyEvents(zero: Ingestion, events: NonEmptyList[Change[IngestionEvent]]): Ingestion =
      events.foldLeft(zero) { (state, event) =>
        event.payload match {
          case IngestionEvent.IngestionStarted(_, _) =>
            throw IllegalArgumentException("Unexpected event")

          case IngestionEvent.IngestionFilesResolved(files) =>
            state.copy(status = Ingestion.Status.Resolved(files))

          case IngestionEvent.IngestionFileProcessed(file) =>
            state.status match {
              case Ingestion.Status.Processing(remaining, processed, failed) =>
                state.copy(status = Ingestion.Status.Processing(remaining - file, processed + file, failed))

              case Ingestion.Status.Resolved(files) =>
                state.copy(status = Ingestion.Status.Processing(files - file, Set() + file, Set()))

              case _ => state
            }

          case IngestionEvent.IngestionFileFailed(file) =>
            state.status match {
              case Ingestion.Status.Processing(remaining, processed, failed) =>
                state.copy(status = Ingestion.Status.Processing(remaining - file, processed, failed + file))

              case Ingestion.Status.Resolved(files) =>
                state.copy(status = Ingestion.Status.Processing(files - file, Set(), Set() + file))

              case _ => state
            }

          case IngestionEvent.IngestionCompleted() =>
            state

        }
      }

  }

}

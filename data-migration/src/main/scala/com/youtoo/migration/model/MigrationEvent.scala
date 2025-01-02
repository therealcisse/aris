package com.youtoo
package migration
package model

import com.youtoo.cqrs.*
import com.youtoo.migration.model.*
import com.youtoo.cqrs.domain.*

import cats.implicits.*

import zio.*
import zio.prelude.*
import zio.schema.*

enum MigrationEvent {
  case MigrationRegistered(id: Migration.Id, timestamp: Timestamp)
  case ExecutionStarted(id: Execution.Id, timestamp: Timestamp)
  case ProcessingStarted(id: Execution.Id, key: Key)
  case KeyProcessed(id: Execution.Id, key: Key)
  case ProcessingFailed(id: Execution.Id, key: Key)
  case ExecutionStopped(id: Execution.Id, timestamp: Timestamp)
  case ExecutionFinished(id: Execution.Id, timestamp: Timestamp)
  case ExecutionFailed(id: Execution.Id, timestamp: Timestamp)
}

type MigrationEventHandler = EventHandler[MigrationEvent, Migration]

object MigrationEvent {
  val discriminator: Discriminator = Discriminator("Migration")

  given Schema[MigrationEvent] = DeriveSchema.gen

  given MetaInfo[MigrationEvent]  {
    extension (self: MigrationEvent)
      def namespace: Namespace = self match {
        case MigrationEvent.MigrationRegistered(_, _) => NS.MigrationRegistered
        case MigrationEvent.ExecutionStarted(_, _) => NS.ExecutionStarted
        case MigrationEvent.ProcessingStarted(_, _) => NS.ProcessingStarted
        case MigrationEvent.KeyProcessed(_, _) => NS.KeyProcessed
        case MigrationEvent.ProcessingFailed(_, _) => NS.ProcessingFailed
        case MigrationEvent.ExecutionStopped(_, _) => NS.ExecutionStopped
        case MigrationEvent.ExecutionFinished(_, _) => NS.ExecutionFinished
        case MigrationEvent.ExecutionFailed(_, _) => NS.ExecutionFailed
      }

    extension (self: MigrationEvent)
      def hierarchy: Option[Hierarchy] = self match {
        case MigrationEvent.MigrationRegistered(_, _) => None
        case MigrationEvent.ExecutionStarted(_, _) => None
        case MigrationEvent.ProcessingStarted(id, _) => Hierarchy.Child(id.asKey).some
        case MigrationEvent.KeyProcessed(id, _) => Hierarchy.Child(id.asKey).some
        case MigrationEvent.ProcessingFailed(id, _) => Hierarchy.Child(id.asKey).some
        case MigrationEvent.ExecutionStopped(_, _) => None
        case MigrationEvent.ExecutionFinished(_, _) => None
        case MigrationEvent.ExecutionFailed(_, _) => None
      }

    extension (self: MigrationEvent) def props: Chunk[EventProperty] = Chunk.empty
    extension (self: MigrationEvent) def reference: Option[ReferenceKey] = None

  }

  object NS {
    val MigrationRegistered = Namespace(0)
    val ExecutionStarted = Namespace(100)
    val ProcessingStarted = Namespace(200)
    val KeyProcessed = Namespace(300)
    val ProcessingFailed = Namespace(400)
    val ExecutionStopped = Namespace(500)
    val ExecutionFinished = Namespace(600)
    val ExecutionFailed = Namespace(700)
  }

  given MigrationEventHandler  {

    def applyEvents(events: NonEmptyList[Change[MigrationEvent]]): Migration =
      events match {
        case NonEmptyList.Single(ch) =>
          ch.payload match {
            case MigrationEvent.MigrationRegistered(id, timestamp) =>
              Migration(id = id, state = Migration.State.empty, timestamp = timestamp)

            case _ => throw IllegalArgumentException("Unexpected event, current state is empty")
          }

        case NonEmptyList.Cons(ch, ls) =>
          ch.payload match {
            case MigrationEvent.MigrationRegistered(id, timestamp) =>
              applyEvents(zero = Migration(id = id, state = Migration.State.empty, timestamp = timestamp), ls)

            case _ => throw IllegalArgumentException("Unexpected event, current state is empty")
          }
      }

    def applyEvents(zero: Migration, events: NonEmptyList[Change[MigrationEvent]]): Migration =
      events.foldLeft(zero) { (state, event) =>

        val newState = event.payload match {
          case MigrationEvent.MigrationRegistered(_, _) =>
            throw IllegalArgumentException(s"Unexpected event, current state is ${event.payload.getClass.getName}")

          case MigrationEvent.ExecutionStarted(id, timestamp) =>
            // Create a new Execution.Started and add it to the executions Map
            val stats = Stats(processing = Set.empty, processed = Set.empty, failed = Set.empty)
            val execution = Execution.Processing(id, stats, timestamp)
            val newExecutions = state.state.executions + (id -> execution)
            val newState = state.state.copy(executions = newExecutions)
            state.copy(state = newState)

          case MigrationEvent.ProcessingStarted(id, key) =>
            // Update the execution to include the new processing key
            val execution = state.state.executions
              .get(id)
              .getOrElse(
                throw new IllegalArgumentException(s"Execution  id $id not found"),
              )
            execution match {
              case processing: Execution.Processing =>
                // Add key to processing
                val newStats = processing.stats.copy(processing = processing.stats.processing + key)
                val newProcessing = processing.copy(stats = newStats)
                val newExecutions = state.state.executions + (id -> newProcessing)
                val newState = state.state.copy(executions = newExecutions)
                state.copy(state = newState)

              case _ =>
                throw new IllegalArgumentException(s"Unexpected execution state for id $id")
            }

          case MigrationEvent.KeyProcessed(id, key) =>
            // Update execution stats for processed key
            val execution = state.state.executions
              .get(id)
              .getOrElse(
                throw new IllegalArgumentException(s"Execution  id $id not found"),
              )

            execution match {
              case processing: Execution.Processing =>
                val newProcessingSet = processing.stats.processing - key
                val newProcessedSet = processing.stats.processed + key
                val newStats = processing.stats.copy(
                  processing = newProcessingSet,
                  processed = newProcessedSet,
                )
                val newProcessing = processing.copy(stats = newStats)
                val newExecutions = state.state.executions + (id -> newProcessing)
                val newState = state.state.copy(executions = newExecutions)
                state.copy(state = newState)

              case _ =>
                throw new IllegalArgumentException(
                  s"Unexpected execution state for id $id: ${execution.getClass.getName}",
                )
            }

          case MigrationEvent.ProcessingFailed(id, key) =>
            // Update execution stats for failed key
            val execution = state.state.executions
              .get(id)
              .getOrElse(
                throw new IllegalArgumentException(s"Execution  id $id not found"),
              )

            execution match {
              case processing: Execution.Processing =>
                val newProcessingSet = processing.stats.processing - key
                val newFailedSet = processing.stats.failed + key
                val newStats = processing.stats.copy(
                  processing = newProcessingSet,
                  failed = newFailedSet,
                )
                val newProcessing = processing.copy(stats = newStats)
                val newExecutions = state.state.executions + (id -> newProcessing)
                val newState = state.state.copy(executions = newExecutions)
                state.copy(state = newState)

              case _ =>
                throw new IllegalArgumentException(s"Unexpected execution state for id $id")
            }

          case MigrationEvent.ExecutionStopped(id, timestamp) =>
            // Transition execution to Stopped state
            val execution = state.state.executions
              .get(id)
              .getOrElse(
                throw new IllegalArgumentException(s"Execution  id $id not found"),
              )

            execution match {
              case processing: Execution.Processing =>
                val stoppedExecution = Execution.Stopped(
                  processing = processing,
                  timestamp = timestamp,
                )
                val newExecutions = state.state.executions + (id -> stoppedExecution)
                val newState = state.state.copy(executions = newExecutions)
                state.copy(state = newState)

              case _ =>
                throw new IllegalArgumentException(s"Unexpected execution state for id $id")
            }

          case MigrationEvent.ExecutionFinished(id, timestamp) =>
            // Transition execution to Finished state
            val execution = state.state.executions
              .get(id)
              .getOrElse(
                throw new IllegalArgumentException(s"Execution  id $id not found"),
              )

            execution match {
              case processing: Execution.Processing =>
                val finishedExecution = Execution.Finished(
                  processing = processing,
                  timestamp = timestamp,
                )
                val newExecutions = state.state.executions + (id -> finishedExecution)
                val newState = state.state.copy(executions = newExecutions)
                state.copy(state = newState)

              case _ =>
                throw new IllegalArgumentException(s"Unexpected execution state for id $id")
            }

          case MigrationEvent.ExecutionFailed(id, timestamp) =>
            // Transition execution to Finished state
            val execution = state.state.executions
              .get(id)
              .getOrElse(
                throw new IllegalArgumentException(s"Execution  id $id not found"),
              )

            execution match {
              case processing: Execution.Processing =>
                val failedExecution = Execution.Failed(
                  processing = processing,
                  timestamp = timestamp,
                )
                val newExecutions = state.state.executions + (id -> failedExecution)
                val newState = state.state.copy(executions = newExecutions)
                state.copy(state = newState)

              case _ =>
                throw new IllegalArgumentException(s"Unexpected execution state for id $id")
            }
        }

        // Return the updated migration state
        newState
      }
  }
}

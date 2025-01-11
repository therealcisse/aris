package com.youtoo
package source
package model

import cats.implicits.*

import zio.*

import zio.schema.{DeriveSchema, Schema}
import zio.prelude.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*

enum SourceEvent {
  case Added(id: SourceDefinition.Id, info: SourceType)
  case Deleted(id: SourceDefinition.Id)
}

object SourceEvent {
  val discriminator: Discriminator = Discriminator("Source")

  given Schema[SourceEvent] = DeriveSchema.gen

  given MetaInfo[SourceEvent] {
    extension (self: SourceEvent)
      def namespace: Namespace = self match {
        case SourceEvent.Added(_, _)    => NS.Added
        case SourceEvent.Deleted(_) => NS.Deleted
      }

    extension (self: SourceEvent) def hierarchy = None
    extension (self: SourceEvent) def props = Chunk.empty
    extension (self: SourceEvent) def reference = None
  }

  object NS {
    val Added = Namespace(0)
    val Deleted = Namespace(100)
  }

  private def updateSource(id: SourceDefinition.Id, info: SourceType, timestamp: Timestamp)(state: Option[SourceDefinition]): Option[SourceDefinition] =
    state match {
      case None => SourceDefinition(id, info, created = timestamp, updated = None).some
      case Some(SourceDefinition(_, _, created, _)) => SourceDefinition(id, info, created = created, updated = Some(timestamp)).some
    }

  class LoadSource() extends EventHandler[SourceEvent, Option[SourceDefinition]] {
    def applyEvents(events: NonEmptyList[Change[SourceEvent]]): Option[SourceDefinition] =
      applyEvents(None, events)

    def applyEvents(zero: Option[SourceDefinition], events: NonEmptyList[Change[SourceEvent]]): Option[SourceDefinition] =
      events.foldLeft(zero) { (state, ch) =>
        ch.payload match {
          case SourceEvent.Added(id, info) => updateSource(id, info, ch.version.timestamp)(state)
          case SourceEvent.Deleted(_) => None
        }
      }

  }

  class LoadSources() extends EventHandler[SourceEvent, Map[SourceDefinition.Id, SourceDefinition]] {
    def applyEvents(events: NonEmptyList[Change[SourceEvent]]): Map[SourceDefinition.Id, SourceDefinition] =
      applyEvents(Map.empty[SourceDefinition.Id, SourceDefinition], events)

    def applyEvents(zero: Map[SourceDefinition.Id, SourceDefinition], events: NonEmptyList[Change[SourceEvent]]): Map[SourceDefinition.Id, SourceDefinition] =
      events.foldLeft(zero) { (state, ch) =>
        ch.payload match {
          case SourceEvent.Added(id, info) => state.updatedWith(id)(updateSource(id, info, ch.version.timestamp))
          case SourceEvent.Deleted(id) => state - id
        }
      }

  }

}


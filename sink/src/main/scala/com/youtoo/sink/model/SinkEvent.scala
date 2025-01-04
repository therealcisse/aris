package com.youtoo
package sink
package model

import cats.implicits.*

import zio.*

import zio.schema.{DeriveSchema, Schema}
import zio.prelude.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*

enum SinkEvent {
  case Added(id: SinkDefinition.Id, info: SinkType)
  case Deleted(id: SinkDefinition.Id)
}

object SinkEvent {
  val discriminator: Discriminator = Discriminator("Sink")

  given Schema[SinkEvent] = DeriveSchema.gen

  given MetaInfo[SinkEvent] {
    extension (self: SinkEvent)
      def namespace: Namespace = self match {
        case SinkEvent.Added(_, _)    => NS.Added
        case SinkEvent.Deleted(_) => NS.Deleted
      }

    extension (self: SinkEvent) def hierarchy = None
    extension (self: SinkEvent) def props = Chunk.empty
    extension (self: SinkEvent) def reference = None
  }

  object NS {
    val Added = Namespace(0)
    val Deleted = Namespace(100)
  }

  private def updateSink(id: SinkDefinition.Id, info: SinkType, timestamp: Timestamp)(state: Option[SinkDefinition]): Option[SinkDefinition] =
    state match {
      case None => SinkDefinition(id, info, created = timestamp, updated = None).some
      case Some(SinkDefinition(_, _, created, _)) => SinkDefinition(id, info, created = created, updated = Some(timestamp)).some
    }

  class LoadSink() extends EventHandler[SinkEvent, Option[SinkDefinition]] {
    def applyEvents(events: NonEmptyList[Change[SinkEvent]]): Option[SinkDefinition] =
      applyEvents(None, events)

    def applyEvents(zero: Option[SinkDefinition], events: NonEmptyList[Change[SinkEvent]]): Option[SinkDefinition] =
      events.foldLeft(zero) { (state, ch) =>
        ch.payload match {
          case SinkEvent.Added(id, info) => updateSink(id, info, ch.version.timestamp)(state)
          case SinkEvent.Deleted(_) => None
        }
      }

  }

  class LoadSinks() extends EventHandler[SinkEvent, Map[SinkDefinition.Id, SinkDefinition]] {
    def applyEvents(events: NonEmptyList[Change[SinkEvent]]): Map[SinkDefinition.Id, SinkDefinition] =
      applyEvents(Map.empty[SinkDefinition.Id, SinkDefinition], events)

    def applyEvents(zero: Map[SinkDefinition.Id, SinkDefinition], events: NonEmptyList[Change[SinkEvent]]): Map[SinkDefinition.Id, SinkDefinition] =
      events.foldLeft(zero) { (state, ch) =>
        ch.payload match {
          case SinkEvent.Added(id, info) => state.updatedWith(id)(updateSink(id, info, ch.version.timestamp))
          case SinkEvent.Deleted(id) => state - id
        }
      }

  }

}


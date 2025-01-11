package com.youtoo
package ingestion
package model

import cats.implicits.*

import zio.*

import zio.schema.{DeriveSchema, Schema}
import zio.prelude.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*

enum IngestionConfigEvent {
  case ConnectionAdded(connection: IngestionConfig.Connection)
}

object IngestionConfigEvent {
  val discriminator: Discriminator = Discriminator("IngestionConfig")

  given Schema[IngestionConfigEvent] = DeriveSchema.gen

  given MetaInfo[IngestionConfigEvent] {
    extension (self: IngestionConfigEvent)
      def namespace: Namespace = self match {
        case IngestionConfigEvent.ConnectionAdded(_) => NS.ConnectionAdded
      }

    extension (self: IngestionConfigEvent) def hierarchy: Option[Hierarchy] = None
    extension (self: IngestionConfigEvent) def props: Chunk[EventProperty] = Chunk.empty

    extension (self: IngestionConfigEvent) def reference: Option[ReferenceKey] = self match {
      case IngestionConfigEvent.ConnectionAdded(conn) => ReferenceKey(conn.id.asKey.value).some
    }

  }

  object NS {
    val ConnectionAdded = Namespace(0)
  }

  private def updateIngestionConfig(event: IngestionConfigEvent)(state: Option[IngestionConfig]): Option[IngestionConfig] =
    (state, event) match {
      case (None, IngestionConfigEvent.ConnectionAdded(conn)) => Some(IngestionConfig(Chunk(conn)))
      case (Some(mc), IngestionConfigEvent.ConnectionAdded(conn)) => mc.copy(
        connections = mc.connections.filterNot(_.id == conn.id) ++ Chunk(conn)
      ).some
    }

  class LoadIngestionConfig() extends EventHandler[IngestionConfigEvent, Option[IngestionConfig]] {
    def applyEvents(events: NonEmptyList[Change[IngestionConfigEvent]]): Option[IngestionConfig] =
      applyEvents(None, events)

    def applyEvents(zero: Option[IngestionConfig], events: NonEmptyList[Change[IngestionConfigEvent]]): Option[IngestionConfig] =
      events.foldLeft(zero) { (state, ch) =>
        updateIngestionConfig(ch.payload)(state)
      }

  }

}

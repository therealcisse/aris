package com.github
package aris
package tenants

import aris.*
import Codecs.protobuf.given

import zio.*
import zio.schema.*
import zio.schema.codec.*
import zio.prelude.*

enum TenantCommand {
  case AddTenant(id: Namespace, name: String, description: String, timestamp: Timestamp)
  case DeleteTenant(id: Namespace, timestamp: Timestamp)
  case DisableTenant(id: Namespace, timestamp: Timestamp)
  case EnableTenant(id: Namespace, timestamp: Timestamp)
}

enum TenantEvent {
  case TenantAdded(id: Namespace, name: String, description: String, timestamp: Timestamp)
  case TenantDeleted(id: Namespace, timestamp: Timestamp)
  case TenantDisabled(id: Namespace, timestamp: Timestamp)
  case TenantEnabled(id: Namespace, timestamp: Timestamp)
}

object TenantEvent {
  given Schema[TenantEvent] = DeriveSchema.gen[TenantEvent]
  given BinaryCodec[TenantEvent] = protobufCodec[TenantEvent]
  given Tag[TenantEvent] = Tag[TenantEvent]

  given MetaInfo[TenantEvent] with
    extension (e: TenantEvent) def timestamp: Option[Timestamp] =
      e match {
        case TenantEvent.TenantAdded(_, _, _, ts) => Some(ts)
        case TenantEvent.TenantDeleted(_, ts)     => Some(ts)
        case TenantEvent.TenantDisabled(_, ts)    => Some(ts)
        case TenantEvent.TenantEnabled(_, ts)     => Some(ts)
      }
}

object TenantCommand {
  given CmdHandler[TenantCommand, TenantEvent] with
    def applyCmd(cmd: TenantCommand): NonEmptyList[TenantEvent] =
      cmd match {
        case TenantCommand.AddTenant(id, name, desc, ts) =>
          NonEmptyList.single(TenantEvent.TenantAdded(id, name, desc, ts))
        case TenantCommand.DeleteTenant(id, ts) =>
          NonEmptyList.single(TenantEvent.TenantDeleted(id, ts))
        case TenantCommand.DisableTenant(id, ts) =>
          NonEmptyList.single(TenantEvent.TenantDisabled(id, ts))
        case TenantCommand.EnableTenant(id, ts) =>
          NonEmptyList.single(TenantEvent.TenantEnabled(id, ts))
      }
}

object NameTenantEventHandler extends EventHandler[TenantEvent, NameTenant] {
  def applyEvents(events: NonEmptyList[Change[TenantEvent]]): Option[NameTenant] =
    events.foldLeft(Option.empty[NameTenant]) { (tenant, change) =>
      change.payload match {
        case TenantEvent.TenantAdded(id, name, desc, created) =>
          Some(NameTenant(id, name, desc, created, NameTenant.Status.Active))
        case TenantEvent.TenantDeleted(_, _) =>
          tenant.map(_.copy(status = NameTenant.Status.Deleted))
        case TenantEvent.TenantDisabled(_, _) =>
          tenant.map(_.copy(status = NameTenant.Status.Disabled))
        case TenantEvent.TenantEnabled(_, _) =>
          tenant.map(_.copy(status = NameTenant.Status.Active))
      }
    }

  def applyEvents(zero: NameTenant, events: NonEmptyList[Change[TenantEvent]]): Option[NameTenant] =
    Some(events.foldLeft(zero) { (tenant, change) =>
      change.payload match {
        case TenantEvent.TenantAdded(id, name, desc, created) =>
          tenant.copy(id = id, name = name, description = desc, created = created, status = NameTenant.Status.Active)
        case TenantEvent.TenantDeleted(_, _) =>
          tenant.copy(status = NameTenant.Status.Deleted)
        case TenantEvent.TenantDisabled(_, _) =>
          tenant.copy(status = NameTenant.Status.Disabled)
        case TenantEvent.TenantEnabled(_, _) =>
          tenant.copy(status = NameTenant.Status.Active)
      }
    })
}

object NameTenantsEventHandler extends EventHandler[TenantEvent, NonEmptyList[NameTenant]] {
  private def groupId(ev: TenantEvent): Namespace =
    ev match {
      case TenantEvent.TenantAdded(id, _, _, _)  => id
      case TenantEvent.TenantDeleted(id, _)      => id
      case TenantEvent.TenantDisabled(id, _)     => id
      case TenantEvent.TenantEnabled(id, _)      => id
    }

  def applyEvents(events: NonEmptyList[Change[TenantEvent]]): Option[NonEmptyList[NameTenant]] =
    val groups = events.toChunk.groupBy(ch => groupId(ch.payload))
    val tenants = groups.values.flatMap { chunk =>
      NonEmptyList.fromChunk(chunk).flatMap(NameTenantEventHandler.applyEvents)
    }
    NonEmptyList.fromChunk(Chunk.fromIterable(tenants))

  def applyEvents(zero: NonEmptyList[NameTenant], events: NonEmptyList[Change[TenantEvent]]): Option[NonEmptyList[NameTenant]] =
    val zeroMap = zero.toChunk.map(t => t.id -> t).toMap
    val groups = events.toChunk.groupBy(ch => groupId(ch.payload))
    val result = groups.foldLeft(zeroMap) { case (acc, (id, chunk)) =>
      NonEmptyList.fromChunk(chunk).flatMap(NameTenantEventHandler.applyEvents) match {
        case Some(t) => acc.updated(id, t)
        case None    => acc
      }
    }
    NonEmptyList.fromChunk(Chunk.fromIterable(result.values))
}

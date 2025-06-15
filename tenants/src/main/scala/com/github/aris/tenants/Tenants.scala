package com.github
package aris
package tenants

import com.github.aris.*
import com.github.aris.domain.*

import zio.*
import zio.schema.*
import zio.schema.codec.*
import zio.prelude.*

enum TenantCommand {
  case AddTenant(id: TenantId, namespace: Namespace, name: String, description: String, timestamp: Timestamp)
  case DeleteTenant(id: TenantId, namespace: Namespace, timestamp: Timestamp)
  case DisableTenant(id: TenantId, namespace: Namespace, timestamp: Timestamp)
  case EnableTenant(id: TenantId, namespace: Namespace, timestamp: Timestamp)
}

enum TenantEvent {
  case TenantAdded(id: TenantId, namespace: Namespace, name: String, description: String, timestamp: Timestamp)
  case TenantDeleted(id: TenantId, namespace: Namespace, timestamp: Timestamp)
  case TenantDisabled(id: TenantId, namespace: Namespace, timestamp: Timestamp)
  case TenantEnabled(id: TenantId, namespace: Namespace, timestamp: Timestamp)
}

object TenantEvent {
  given Schema[TenantEvent] = DeriveSchema.gen[TenantEvent]
  given BinaryCodec[TenantEvent] = ProtobufCodec.protobufCodec[TenantEvent]

  given MetaInfo[TenantEvent] {

    extension (e: TenantEvent) def timestamp: Option[Timestamp] =
      e match {
        case TenantEvent.TenantAdded(_, _, _, _, ts) => Some(ts)
        case TenantEvent.TenantDeleted(_, _, ts)     => Some(ts)
        case TenantEvent.TenantDisabled(_, _, ts)    => Some(ts)
        case TenantEvent.TenantEnabled(_, _, ts)     => Some(ts)
      }

    extension (e: TenantEvent) override def tags: Set[EventTag] =
      Set(EventTag("tenant"))
  }
}

object TenantCommand {
  given CmdHandler[TenantCommand, TenantEvent] {

    def applyCmd(cmd: TenantCommand): NonEmptyList[TenantEvent] =
      cmd match {
        case TenantCommand.AddTenant(id, ns, name, desc, ts) =>
          NonEmptyList.single(TenantEvent.TenantAdded(id, ns, name, desc, ts))
        case TenantCommand.DeleteTenant(id, ns, ts) =>
          NonEmptyList.single(TenantEvent.TenantDeleted(id, ns, ts))
        case TenantCommand.DisableTenant(id, ns, ts) =>
          NonEmptyList.single(TenantEvent.TenantDisabled(id, ns, ts))
        case TenantCommand.EnableTenant(id, ns, ts) =>
          NonEmptyList.single(TenantEvent.TenantEnabled(id, ns, ts))
      }
  }
}

object NameTenantEventHandler extends EventHandler[TenantEvent, NameTenant] {
  def applyEvents(events: NonEmptyList[Change[TenantEvent]]): Option[NameTenant] =
    events.foldLeft(Option.empty[NameTenant]) { (tenant, change) =>
      change.payload match {
        case TenantEvent.TenantAdded(id, ns, name, desc, created) =>
          Some(NameTenant(id, ns, name, desc, created, NameTenant.Status.Active))
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
        case TenantEvent.TenantAdded(id, ns, name, desc, created) =>
          tenant.copy(id = id, namespace = ns, name = name, description = desc, created = created, status = NameTenant.Status.Active)
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
  private def groupId(ev: TenantEvent): TenantId =
    ev match {
      case TenantEvent.TenantAdded(id, _, _, _, _)  => id
      case TenantEvent.TenantDeleted(id, _, _)      => id
      case TenantEvent.TenantDisabled(id, _, _)     => id
      case TenantEvent.TenantEnabled(id, _, _)      => id
    }

  def applyEvents(events: NonEmptyList[Change[TenantEvent]]): Option[NonEmptyList[NameTenant]] =
    val groups = events.toChunk.groupBy(ch => groupId(ch.payload))
    val tenants = groups.values.flatMap { chunk =>
      NonEmptyList.fromIterableOption(chunk).flatMap(NameTenantEventHandler.applyEvents)
    }
    NonEmptyList.fromIterableOption(tenants)

  def applyEvents(zero: NonEmptyList[NameTenant], events: NonEmptyList[Change[TenantEvent]]): Option[NonEmptyList[NameTenant]] =
    val zeroMap = zero.toChunk.map(t => t.id -> t).toMap
    val groups = events.toChunk.groupBy(ch => groupId(ch.payload))
    val result = groups.foldLeft(zeroMap) { case (acc, (id, chunk)) =>
      NonEmptyList.fromIterableOption(chunk).flatMap(NameTenantEventHandler.applyEvents) match {
        case Some(t) => acc.updated(id, t)
        case None    => acc
      }
    }
    NonEmptyList.fromIterableOption(result.values)
}

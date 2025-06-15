package com.github
package aris
package tenants

import com.github.aris.*
import com.github.aris.service.CQRSPersistence
import com.github.aris.projection.*

import zio.*
import zio.stream.*

object TenantProjection {
  val tag: EventTag = EventTag("tenant")
  private val discriminator = Discriminator("Tenant")
  private val ns = Namespace.root

  val id: Projection.Id = Projection.Id(Projection.Name("tenant-projection"), Projection.VersionId("v1"), ns)

  def apply(
    persistence: CQRSPersistence,
    repository: TenantRepository,
    store: ProjectionManagementStore,
    bufferSize: Int,
  ): Projection = {
    val commit = CommitOffset().afterN(bufferSize).after(5.seconds)

    val handler: Envelope[TenantEvent] => Task[Unit] = {
      case Envelope(_, TenantEvent.TenantAdded(id, name, desc, created), _, _, _) =>
        repository.add(id, name, desc, created)
      case Envelope(_, TenantEvent.TenantDeleted(id, _), _, _, _) =>
        repository.delete(id)
      case Envelope(_, TenantEvent.TenantDisabled(id, _), _, _, _) =>
        repository.disable(id)
      case Envelope(_, TenantEvent.TenantEnabled(id, _), _, _, _) =>
        repository.enable(id)
    }

    def extractId(ev: TenantEvent): Key =
      ev match {
        case TenantEvent.TenantAdded(id, _, _, _)  => id.asKey
        case TenantEvent.TenantDeleted(id, _)      => id.asKey
        case TenantEvent.TenantDisabled(id, _)     => id.asKey
        case TenantEvent.TenantEnabled(id, _)      => id.asKey
      }

    def query(start: Version): ZStream[Any, Throwable, Envelope[TenantEvent]] =
      ZStream.unwrap {
        Ref.make(start).map { ref =>
          ZStream.repeatZIO {
            for {
              off <- ref.get
              opts = FetchOptions().offset(off.asKey).limit(bufferSize)
              events <- persistence.readEvents[TenantEvent](ns, opts, Catalog.Default, tag)
              _ <- ref.update(_ => events.lastOption.map(_.version).getOrElse(off))
            } yield events.map(ch => Envelope(ch.version, ch.payload, ns, discriminator, extractId(ch.payload)))
          }.flattenChunks
        }
      }

    Projection.atLeastOnce(id, handler, query, commit, store)
  }
}

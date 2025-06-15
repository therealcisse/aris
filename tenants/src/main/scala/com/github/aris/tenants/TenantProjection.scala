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
  ): Projection = {
    val commit = CommitOffset(100, 5.seconds)
    val bufferSize = 100

    val handler: Envelope[TenantEvent] => Task[Unit] = {
      case Envelope(_, TenantEvent.TenantAdded(id, ns, name, desc, created), _, _, _) =>
        repository.add(id, ns, name, desc, created)
      case Envelope(_, TenantEvent.TenantDeleted(id, ns, _), _, _, _) =>
        repository.delete(id, ns)
      case Envelope(_, TenantEvent.TenantDisabled(id, ns, _), _, _, _) =>
        repository.disable(id, ns)
      case Envelope(_, TenantEvent.TenantEnabled(id, ns, _), _, _, _) =>
        repository.enable(id, ns)
    }

    def extractId(ev: TenantEvent): Key =
      ev match {
        case TenantEvent.TenantAdded(id, _, _, _, _)  => id.asKey
        case TenantEvent.TenantDeleted(id, _, _)      => id.asKey
        case TenantEvent.TenantDisabled(id, _, _)     => id.asKey
        case TenantEvent.TenantEnabled(id, _, _)      => id.asKey
      }

    def query(bufferSize: Int)(start: Version): ZStream[Any, Throwable, Envelope[TenantEvent]] =
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

    Projection.atLeastOnce(id, handler, query(bufferSize), commit, store)
  }
}

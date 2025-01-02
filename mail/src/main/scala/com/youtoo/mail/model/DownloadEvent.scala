package com.youtoo
package mail
package model

import cats.implicits.*

import com.youtoo.job.model.*
import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.*
import zio.*
import zio.prelude.*
import zio.schema.*

enum DownloadEvent {
  case Downloaded(version: Version, jobId: Job.Id)
}

object DownloadEvent {
  given Schema[DownloadEvent] = DeriveSchema.gen

  val discriminator = Discriminator("Download")

  given MetaInfo[DownloadEvent] {

    extension (self: DownloadEvent)
      def namespace: Namespace = self match {
        case _: DownloadEvent.Downloaded => NS.Downloaded
      }

    extension (self: DownloadEvent) def hierarchy: Option[Hierarchy] = self match {
      case e: DownloadEvent.Downloaded => Hierarchy.Child(e.jobId.asKey).some

    }
    extension (self: DownloadEvent) def props: Chunk[EventProperty] = Chunk.empty
    extension (self: DownloadEvent) def reference: Option[ReferenceKey] = None
  }

  object NS {
    val Downloaded = Namespace(0)
  }

  class LoadVersion() extends EventHandler[DownloadEvent, Version] {
    def applyEvents(events: NonEmptyList[Change[DownloadEvent]]): Version =
      events match {
        case NonEmptyList.Single(ch) =>
          ch.payload match {
            case DownloadEvent.Downloaded(version, _) => version
          }

        case NonEmptyList.Cons(_, ls) =>
          applyEvents(ls)

      }

    def applyEvents(zero: Version, events: NonEmptyList[Change[DownloadEvent]]): Version =
      events.foldLeft(zero) { (state, e) =>
        e.payload match {
          case DownloadEvent.Downloaded(version, _) =>
            version
        }
      }

  }

}


package com.youtoo
package ingestion
package model

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*

import cats.implicits.*

import zio.*
import zio.prelude.*
import zio.schema.*

enum FileEvent {
  case FileAdded(
    provider: Provider.Id,
    id: IngestionFile.Id,
    name: IngestionFile.Name,
    metadata: IngestionFile.Metadata,
    sig: IngestionFile.Sig,
  )
  case ProviderAdded(id: Provider.Id, name: Provider.Name, location: Provider.Location)
}

object FileEvent {
  val discriminator: Discriminator = Discriminator("File")

  given Schema[FileEvent] = DeriveSchema.gen

  given MetaInfo[FileEvent] with {

    extension (self: FileEvent)
      def namespace: Namespace = self match {
        case FileEvent.FileAdded(_, _, _, _, _) => Namespace(0)
        case FileEvent.ProviderAdded(_, _, _) => Namespace(1)

      }

    extension (self: FileEvent)
      def hierarchy: Option[Hierarchy] = self match {
        case FileEvent.FileAdded(provider, _, _, _, _) => Hierarchy.Child(provider.asKey).some
        case FileEvent.ProviderAdded(_, _, _) => None

      }

    extension (self: FileEvent)
      def props: Chunk[EventProperty] = self match {
        case FileEvent.FileAdded(_, _, name, _, sig) =>
          Chunk(
            EventProperty("sig", sig.value),
            EventProperty("name", name.value),
          )

        case FileEvent.ProviderAdded(_, name, Provider.Location.File(path)) =>
          Chunk(
            EventProperty("name", name.value),
            EventProperty("locationType", "File"),
            EventProperty("location", path),
          )

      }

  }

  given ingestionFileEventHandler: EventHandler[FileEvent, Option[IngestionFile]] with {

    def applyEvents(events: NonEmptyList[Change[FileEvent]]): Option[IngestionFile] =
      events match {
        case NonEmptyList.Single(ch) =>
          ch.payload match {
            case FileEvent.FileAdded(_, id, name, metadata, sig) =>
              IngestionFile(id, name, metadata, sig).some

            case _ => None
          }

        case NonEmptyList.Cons(ch, ls) =>
          ch.payload match {
            case FileEvent.FileAdded(_, id, name, metadata, sig) =>
              IngestionFile(id, name, metadata, sig).some

            case _ => applyEvents(ls)
          }

      }

    def applyEvents(zero: Option[IngestionFile], events: NonEmptyList[Change[FileEvent]]): Option[IngestionFile] =
      events.foldLeft(zero) {
        case (Some(file), event) =>
          event.payload match {
            case FileEvent.FileAdded(_, _, _, _, _) =>
              throw IllegalArgumentException(s"Unexpected event, current file is ${event.payload.getClass.getName}")

            case _ => file.some
          }

        case (None, event) =>
          event.payload match {
            case FileEvent.FileAdded(_, id, name, metadata, sig) =>
              IngestionFile(id, name, metadata, sig).some

            case _ => None
          }

      }

  }

  given providerEventHandler: EventHandler[FileEvent, Option[Provider]] with {

    def applyEvents(events: NonEmptyList[Change[FileEvent]]): Option[Provider] =
      events match {
        case NonEmptyList.Single(ch) =>
          ch.payload match {
            case FileEvent.ProviderAdded(id, name, location) =>
              Provider(id, name, location).some

            case _ => None
          }

        case NonEmptyList.Cons(ch, ls) =>
          ch.payload match {
            case FileEvent.ProviderAdded(id, name, location) =>
              Provider(id, name, location).some

            case _ => applyEvents(ls)
          }

      }

    def applyEvents(zero: Option[Provider], events: NonEmptyList[Change[FileEvent]]): Option[Provider] =
      events.foldLeft(zero) {
        case (Some(file), event) =>
          event.payload match {
            case FileEvent.FileAdded(_, _, _, _, _) =>
              throw IllegalArgumentException(s"Unexpected event, current file is ${event.payload.getClass.getName}")

            case _ => file.some
          }

        case (None, event) =>
          event.payload match {
            case FileEvent.ProviderAdded(id, name, location) =>
              Provider(id, name, location).some

            case _ => None
          }

      }

  }
}

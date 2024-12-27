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

  given MetaInfo[FileEvent]  {

    extension (self: FileEvent)
      def namespace: Namespace = self match {
        case FileEvent.FileAdded(_, _, _, _, _) => NS.FileAdded
        case FileEvent.ProviderAdded(_, _, _) => NS.ProviderAdded
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

    extension (self: FileEvent) def reference: Option[Reference] = None

  }

  object NS {
    val FileAdded = Namespace(0)
    val ProviderAdded = Namespace(100)
  }

  open class LoadIngestionFileByName(arg: IngestionFile.Name) extends EventHandler[FileEvent, Option[IngestionFile]] {

    def applyEvents(events: NonEmptyList[Change[FileEvent]]): Option[IngestionFile] =
      events match {
        case NonEmptyList.Single(ch) =>
          ch.payload match {
            case FileEvent.FileAdded(_, id, name, metadata, sig) if name == arg =>
              IngestionFile(id, name, metadata, sig).some

            case _ => None
          }

        case NonEmptyList.Cons(ch, ls) =>
          ch.payload match {
            case FileEvent.FileAdded(_, id, name, metadata, sig) if name == arg =>
              IngestionFile(id, name, metadata, sig).some

            case _ => applyEvents(ls)
          }

      }

    def applyEvents(zero: Option[IngestionFile], events: NonEmptyList[Change[FileEvent]]): Option[IngestionFile] =
      events.foldLeft(zero) {
        case (Some(file), event) =>
          event.payload match {
            case FileEvent.FileAdded(_, _, name, _, _) if name == arg =>
              throw IllegalArgumentException(s"Unexpected event, current file is ${event.payload.getClass.getName}")

            case _ => file.some
          }

        case (None, event) =>
          event.payload match {
            case FileEvent.FileAdded(_, id, name, metadata, sig) if name == arg =>
              IngestionFile(id, name, metadata, sig).some

            case _ => None
          }

      }

  }

  open class LoadIngestionFileBySig(arg: IngestionFile.Sig) extends EventHandler[FileEvent, Option[IngestionFile]] {

    def applyEvents(events: NonEmptyList[Change[FileEvent]]): Option[IngestionFile] =
      events match {
        case NonEmptyList.Single(ch) =>
          ch.payload match {
            case FileEvent.FileAdded(_, id, name, metadata, sig) if sig == arg =>
              IngestionFile(id, name, metadata, sig).some

            case _ => None
          }

        case NonEmptyList.Cons(ch, ls) =>
          ch.payload match {
            case FileEvent.FileAdded(_, id, name, metadata, sig) if sig == arg =>
              IngestionFile(id, name, metadata, sig).some

            case _ => applyEvents(ls)
          }

      }

    def applyEvents(zero: Option[IngestionFile], events: NonEmptyList[Change[FileEvent]]): Option[IngestionFile] =
      events.foldLeft(zero) {
        case (Some(file), event) =>
          event.payload match {
            case FileEvent.FileAdded(_, _, _, _, sig) if sig == arg =>
              throw IllegalArgumentException(s"Unexpected event, current file is ${event.payload.getClass.getName}")

            case _ => file.some
          }

        case (None, event) =>
          event.payload match {
            case FileEvent.FileAdded(_, id, name, metadata, sig) if sig == arg =>
              IngestionFile(id, name, metadata, sig).some

            case _ => None
          }

      }

  }

  open class LoadIngestionFile(arg: IngestionFile.Id) extends EventHandler[FileEvent, Option[IngestionFile]] {

    def applyEvents(events: NonEmptyList[Change[FileEvent]]): Option[IngestionFile] =
      events match {
        case NonEmptyList.Single(ch) =>
          ch.payload match {
            case FileEvent.FileAdded(_, id, name, metadata, sig) if id == arg =>
              IngestionFile(id, name, metadata, sig).some

            case _ => None
          }

        case NonEmptyList.Cons(ch, ls) =>
          ch.payload match {
            case FileEvent.FileAdded(_, id, name, metadata, sig) if id == arg =>
              IngestionFile(id, name, metadata, sig).some

            case _ => applyEvents(ls)
          }

      }

    def applyEvents(zero: Option[IngestionFile], events: NonEmptyList[Change[FileEvent]]): Option[IngestionFile] =
      events.foldLeft(zero) {
        case (Some(file), event) =>
          event.payload match {
            case FileEvent.FileAdded(_, id, _, _, _) if id == arg =>
              throw IllegalArgumentException(s"Unexpected event, current file is ${event.payload.getClass.getName}")

            case _ => file.some
          }

        case (None, event) =>
          event.payload match {
            case FileEvent.FileAdded(_, id, name, metadata, sig) if id == arg =>
              IngestionFile(id, name, metadata, sig).some

            case _ => None
          }

      }

  }

  open class LoadProvider(arg: Provider.Id) extends EventHandler[FileEvent, Option[Provider]] {

    def applyEvents(events: NonEmptyList[Change[FileEvent]]): Option[Provider] =
      events match {
        case NonEmptyList.Single(ch) =>
          ch.payload match {
            case FileEvent.ProviderAdded(id, name, location) if id == arg =>
              Provider(id, name, location).some

            case _ => None
          }

        case NonEmptyList.Cons(ch, ls) =>
          ch.payload match {
            case FileEvent.ProviderAdded(id, name, location) if id == arg =>
              Provider(id, name, location).some

            case _ => applyEvents(ls)
          }

      }

    def applyEvents(zero: Option[Provider], events: NonEmptyList[Change[FileEvent]]): Option[Provider] =
      events.foldLeft(zero) {
        case (Some(provider), event) =>
          event.payload match {
            case FileEvent.ProviderAdded(id, _, _) if id == arg =>
              throw IllegalArgumentException(s"Unexpected event, current file is ${event.payload.getClass.getName}")

            case _ => provider.some
          }

        case (None, event) =>
          event.payload match {
            case FileEvent.ProviderAdded(id, name, location) if id == arg =>
              Provider(id, name, location).some

            case _ => None
          }

      }

  }

  open class LoadProviders() extends EventHandler[FileEvent, List[Provider]] {

    def applyEvents(events: NonEmptyList[Change[FileEvent]]): List[Provider] =
      events match {
        case NonEmptyList.Single(ch) =>
          ch.payload match {
            case FileEvent.ProviderAdded(id, name, location) =>
              List(Provider(id, name, location))

            case _ => Nil
          }

        case NonEmptyList.Cons(ch, ls) =>
          ch.payload match {
            case FileEvent.ProviderAdded(id, name, location) =>
              applyEvents(List(Provider(id, name, location)), ls)

            case _ => applyEvents(ls)
          }

      }

    def applyEvents(zero: List[Provider], events: NonEmptyList[Change[FileEvent]]): List[Provider] =
      events.foldLeft(zero) { (providers, event) =>
        event.payload match {
          case FileEvent.ProviderAdded(id, name, location) =>
            Provider(id, name, location) :: providers

          case _ => providers
        }

      }

  }

  open class LoadFiles(arg: Provider.Id) extends EventHandler[FileEvent, Option[NonEmptyList[IngestionFile]]] {

    def applyEvents(events: NonEmptyList[Change[FileEvent]]): Option[NonEmptyList[IngestionFile]] =
      events match {
        case NonEmptyList.Single(ch) =>
          ch.payload match {
            case FileEvent.FileAdded(provider, id, name, metadata, sig) if provider == arg =>
              NonEmptyList(IngestionFile(id, name, metadata, sig)).some

            case _ => None
          }

        case NonEmptyList.Cons(ch, ls) =>
          ch.payload match {
            case FileEvent.FileAdded(provider, id, name, metadata, sig) if provider == arg =>
              applyEvents(NonEmptyList(IngestionFile(id, name, metadata, sig)).some, ls)

            case _ => applyEvents(ls)
          }

      }

    def applyEvents(
      zero: Option[NonEmptyList[IngestionFile]],
      events: NonEmptyList[Change[FileEvent]],
    ): Option[NonEmptyList[IngestionFile]] =
      events.foldLeft(zero) {
        case (Some(nel), event) =>
          event.payload match {
            case FileEvent.FileAdded(provider, id, name, metadata, sig) if provider == arg =>
              (IngestionFile(id, name, metadata, sig) :: nel).some

            case _ => nel.some
          }

        case (None, event) =>
          event.payload match {
            case FileEvent.FileAdded(provider, id, name, metadata, sig) if provider == arg =>
              NonEmptyList(IngestionFile(id, name, metadata, sig)).some

            case _ => None
          }

      }

  }

}

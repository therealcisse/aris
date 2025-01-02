package com.youtoo
package mail
package model

import cats.implicits.*

import zio.*
import zio.prelude.*

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.*

enum AuthorizationEvent {
  case AuthorizationGranted(token: TokenInfo, timestamp: Timestamp)
  case AuthorizationRevoked(timestamp: Timestamp)
}

object AuthorizationEvent {
  import zio.schema.*

  given Schema[AuthorizationEvent] = DeriveSchema.gen

  val discriminator = Discriminator("Authorization")

  given MetaInfo[AuthorizationEvent] {

    extension (self: AuthorizationEvent)
      def namespace: Namespace = self match {
        case _: AuthorizationEvent.AuthorizationGranted => NS.AuthorizationGranted
        case _: AuthorizationEvent.AuthorizationRevoked => NS.AuthorizationRevoked

      }

    extension (self: AuthorizationEvent) def hierarchy: Option[Hierarchy] = self match {
      case _: AuthorizationEvent.AuthorizationGranted => None
      case _: AuthorizationEvent.AuthorizationRevoked => None

    }
    extension (self: AuthorizationEvent) def props: Chunk[EventProperty] = Chunk.empty
    extension (self: AuthorizationEvent) def reference: Option[Reference] = None
  }

  object NS {
    val AuthorizationGranted = Namespace(0)
    val AuthorizationRevoked = Namespace(100)
  }

  class LoadAuthorization() extends EventHandler[AuthorizationEvent, Authorization] {
    def applyEvents(events: NonEmptyList[Change[AuthorizationEvent]]): Authorization =
      applyEvents(Authorization.Pending(), events)

    def applyEvents(
        zero: Authorization,
        events: NonEmptyList[Change[AuthorizationEvent]],
    ): Authorization =
      events.foldLeft(zero) { (state, event) =>
        event.payload match {
          case AuthorizationEvent.AuthorizationGranted(token, timestamp) =>
            Authorization.Granted(token, timestamp)
          case AuthorizationEvent.AuthorizationRevoked(timestamp) =>
            Authorization.Revoked(timestamp)
        }
      }

  }

}


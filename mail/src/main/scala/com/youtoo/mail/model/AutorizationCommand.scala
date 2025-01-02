package com.youtoo
package mail
package model

import zio.prelude.*

import com.youtoo.cqrs.*
import com.youtoo.job.model.*

enum AuthorizationCommand {
  case GrantAuthorization(token: TokenInfo, timestamp: Timestamp)
  case RevokeAuthorization(timestamp: Timestamp)
}

object AuthorizationCommand {
  import zio.schema.*

  given Schema[AuthorizationCommand] = DeriveSchema.gen

  given CmdHandler[AuthorizationCommand, AuthorizationEvent] {

    def applyCmd(cmd: AuthorizationCommand): NonEmptyList[AuthorizationEvent] =
      cmd match {
        case AuthorizationCommand.GrantAuthorization(token, timestamp) =>
          NonEmptyList(AuthorizationEvent.AuthorizationGranted(token = token, timestamp = timestamp))

        case AuthorizationCommand.RevokeAuthorization(timestamp) =>
          NonEmptyList(AuthorizationEvent.AuthorizationRevoked(timestamp = timestamp))

      }

  }

}



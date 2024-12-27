package com.youtoo
package mail
package model

import zio.*
import zio.prelude.*

import zio.schema.*

case class GoogleClientInfo(
  clientId: GoogleClientInfo.ClientId,
  clientSecret: GoogleClientInfo.ClientSecret,
  redirectUri: GoogleClientInfo.RedirectUri,
)

object GoogleClientInfo {
  given Schema[GoogleClientInfo] = DeriveSchema.gen

  given Config[GoogleClientInfo] =
    (
      Config.string("google_client_id").map(ClientId(_)) zip
        Config.string("google_client_secret").map(ClientSecret(_)) zip
        Config.string("google_redirect_uri").map(RedirectUri(_))
    ).map { case (clientId, clientSecret, redirectUri) =>
      GoogleClientInfo(clientId, clientSecret, redirectUri)
    }

  type ClientId = ClientId.Type
  object ClientId extends Newtype[String] {
    extension (a: Type) def value: String = unwrap(a)
    given Schema[Type] = derive
  }

  type ClientSecret = ClientSecret.Type
  object ClientSecret extends Newtype[String] {
    extension (a: Type) def value: String = unwrap(a)
    given Schema[Type] = derive
  }

  type RedirectUri = RedirectUri.Type
  object RedirectUri extends Newtype[String] {
    extension (a: Type) def value: String = unwrap(a)
    given Schema[Type] = derive
  }

}

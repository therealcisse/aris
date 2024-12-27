package com.youtoo
package mail
package model

import zio.prelude.*

case class AuthConfig(clientInfo: AuthConfig.ClientInfo)

object AuthConfig {
  import zio.schema.*

  given Schema[AuthConfig] = DeriveSchema.gen

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

  case class ClientInfo(clientId: AuthConfig.ClientId, clientSecret: AuthConfig.ClientSecret, redirectUri: RedirectUri)

  object ClientInfo {
    given Schema[ClientInfo] = DeriveSchema.gen
  }

}

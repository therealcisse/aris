package com.youtoo
package mail
package model

import zio.prelude.*

case class AuthConfig(clientId: AuthConfig.ClientId, clientSecret: AuthConfig.ClientSecret)

object AuthConfig {
  import zio.schema.*

  given Schema[AuthConfig] = DeriveSchema.gen

  type ClientId = ClientId.Type
  object ClientId extends Newtype[String] {
    extension (a: ClientId) def value: String = unwrap(a)
    given Schema[ClientId] = derive
  }

  type ClientSecret = ClientSecret.Type
  object ClientSecret extends Newtype[String] {
    extension (a: ClientSecret) def value: String = unwrap(a)
    given Schema[ClientSecret] = derive
  }
}

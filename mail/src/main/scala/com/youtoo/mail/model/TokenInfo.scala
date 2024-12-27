package com.youtoo
package mail
package model

import zio.prelude.*

import zio.schema.*

case class TokenInfo(refreshToken: TokenInfo.RefreshToken, idToken: TokenInfo.IdToken)

object TokenInfo {
  given Schema[TokenInfo] = DeriveSchema.gen

  type RefreshToken = RefreshToken.Type
  object RefreshToken extends Newtype[String] {
    extension (a: RefreshToken) def value: String = unwrap(a)
    given Schema[RefreshToken] = derive

  }

  type IdToken = IdToken.Type
  object IdToken extends Newtype[String] {
    extension (a: IdToken) def value: String = unwrap(a)
    given Schema[IdToken] = derive

  }

}

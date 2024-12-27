package com.youtoo
package mail
package model

case class AuthConfig()

object AuthConfig {
  import zio.schema.*

  given Schema[AuthConfig] = DeriveSchema.gen

}

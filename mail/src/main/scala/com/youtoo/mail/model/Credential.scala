package com.youtoo
package mail
package model

case class Credential()

object Credential {
  import zio.schema.*

  given Schema[Credential] = DeriveSchema.gen
}

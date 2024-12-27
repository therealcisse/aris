package com.youtoo
package mail

enum AccountType extends Enum[AccountType] {
  case Gmail
}

object AccountType {
  import zio.schema.*

  given Schema[AccountType] = DeriveSchema.gen
}

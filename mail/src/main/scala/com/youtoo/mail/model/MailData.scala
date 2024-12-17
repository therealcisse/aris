package com.youtoo
package mail
package model

import com.youtoo.job.model.*

import zio.prelude.*
import zio.schema.*

case class MailData(
  id: MailData.Id,
  body: MailData.Body,
  accountKey: MailAccount.Id,
  internalDate: InternalDate,
  timestamp: Timestamp,
)

object MailData {
  type Id = Id.Type
  object Id extends Newtype[String] {
    extension (a: Id) def value: String = unwrap(a)

    given Schema[Id] = derive
  }

  type Body = Body.Type
  object Body extends Newtype[String] {
    extension (a: Body) def value: String = unwrap(a)

    given Schema[Body] = derive
  }

  given Schema[MailData] = DeriveSchema.gen
}

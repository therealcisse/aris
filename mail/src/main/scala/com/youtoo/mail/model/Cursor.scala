package com.youtoo
package mail
package model

import zio.*

import zio.schema.*

case class Cursor(timestamp: Timestamp, token: MailToken, total: TotalMessages, isSyncing: Boolean)

object Cursor {
  given Schema[Cursor] = DeriveSchema.gen
}

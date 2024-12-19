package com.youtoo
package mail
package model

import zio.prelude.*

case class SyncConfig(autoSyncSchedule: SyncConfig.CronExpression, autoSyncEnabled: Boolean)

object SyncConfig {
  import zio.schema.*

  given Schema[SyncConfig] = DeriveSchema.gen

  type CronExpression = CronExpression.Type
  object CronExpression extends Newtype[String] {
    extension (a: Type) def value: String = unwrap(a)
    given Schema[CronExpression] = derive
  }
}

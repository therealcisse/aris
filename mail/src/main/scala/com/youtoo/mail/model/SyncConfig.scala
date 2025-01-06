package com.youtoo
package mail
package model

import zio.prelude.*

case class SyncConfig(autoSync: SyncConfig.AutoSync)

object SyncConfig {
  import zio.schema.*

  given Schema[SyncConfig] = DeriveSchema.gen

  type CronExpression = CronExpression.Type
  object CronExpression extends Newtype[String] {
    extension (a: Type) def value: String = unwrap(a)
    given Schema[Type] = derive
  }

  enum AutoSync {
    case enabled(schedule: SyncConfig.CronExpression)
    case disabled(schedule: Option[SyncConfig.CronExpression])

  }

  object AutoSync {
    given Schema[AutoSync] = DeriveSchema.gen

  }

  def default: SyncConfig = SyncConfig(AutoSync.disabled(None))
}

package com.youtoo
package migration
package model

import zio.schema.*

enum ExecutionStatus {
  case registered, running, execution_failed, failed, success, stopped
}

object ExecutionStatus {

  given Schema[ExecutionStatus] = DeriveSchema.gen
}

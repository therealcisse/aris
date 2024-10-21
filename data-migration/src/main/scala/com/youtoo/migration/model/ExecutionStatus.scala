package com.youtoo.cqrs
package migration
package model

import zio.schema.*

enum ExecutionStatus {
  case running, failed, success, stopped
}

object ExecutionStatus {

  given Schema[ExecutionStatus] = DeriveSchema.gen
}

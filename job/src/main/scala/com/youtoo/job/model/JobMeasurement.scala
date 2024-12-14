package com.youtoo
package job
package model

import zio.schema.*

enum JobMeasurement {
  case Deterministic(value: Long)
  case Variable()
}

object JobMeasurement {
  given Schema[JobMeasurement] = DeriveSchema.gen

}

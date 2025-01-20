package com.youtoo
package aris

case class TimeInterval(start: Timestamp, end: Timestamp)

object TimeInterval {
  import zio.schema.*
  given Schema[TimeInterval] = DeriveSchema.gen

  extension (interval: TimeInterval)
    def contains(timestamp: Timestamp): Boolean =
      timestamp.value >= interval.start.value && timestamp.value < interval.end.value

}

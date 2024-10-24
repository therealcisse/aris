package com.youtoo

import zio.*
import zio.prelude.*
import java.time.temporal.ChronoUnit

type Timestamp = Timestamp.Type

object Timestamp extends Newtype[Long] {
  import zio.schema.*

  extension (a: Timestamp) inline def value: Long = Timestamp.unwrap(a)

  def now: Task[Timestamp] = Clock.currentTime(ChronoUnit.MILLIS).map(Timestamp.wrap)

  given Schema[Timestamp] = Schema
    .primitive[Long]
    .transform(
      wrap,
      unwrap,
    )
}

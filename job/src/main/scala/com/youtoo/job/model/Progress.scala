package com.youtoo
package job
package model

import zio.*
import zio.prelude.*

type Progress = Progress.Type

object Progress extends Newtype[Long] {
  import zio.schema.*

  extension (a: Progress) inline def value: Long = Progress.unwrap(a)

  given Schema[Progress] = derive
}

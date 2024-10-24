package com.youtoo

import ulid4j.Ulid

object UlidGenerator {
  private val ulidGenerator = Ulid()

  export ulidGenerator.next as monotonic

  export Ulid.isValid
  export Ulid.unixTime

}

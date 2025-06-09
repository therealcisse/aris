package com.github
package aris

import zio.*
import zio.prelude.*

/** Tag type identifying events. */
type Tag = Tag.Type

object Tag extends Newtype[String] {
  import zio.schema.*
  extension (t: Tag) inline def value: String = Tag.unwrap(t)
  given Schema[Tag] = derive
}

package com.github
package aris

import zio.*
import zio.prelude.*

/** Tag type identifying events. */
type EventTag = EventTag.Type

object EventTag extends Newtype[String] {
  import zio.schema.*
  extension (t: EventTag) inline def value: String = EventTag.unwrap(t)
  given Schema[EventTag] = derive
}

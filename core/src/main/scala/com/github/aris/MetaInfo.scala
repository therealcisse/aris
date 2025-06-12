package com.github
package aris

trait MetaInfo[Event] {
  extension (self: Event) def timestamp: Option[Timestamp]
  extension (self: Event) def tags: Set[EventTag] = Set.empty

}

object MetaInfo {}

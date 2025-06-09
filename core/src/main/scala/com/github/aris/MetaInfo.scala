package com.github
package aris

trait MetaInfo[Event] {
  extension (self: Event) def namespace: Namespace
  extension (self: Event) def timestamp: Option[Timestamp]

}

object MetaInfo {}

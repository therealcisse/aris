package com.youtoo
package aris

trait MetaInfo[Event] {
  extension (self: Event) def namespace: Namespace
  extension (self: Event) def hierarchy: Option[Hierarchy]
  extension (self: Event) def props: zio.Chunk[EventProperty]
  extension (self: Event) def reference: Option[ReferenceKey]
  extension (self: Event) def timestamp: Option[Timestamp]

}

object MetaInfo {}

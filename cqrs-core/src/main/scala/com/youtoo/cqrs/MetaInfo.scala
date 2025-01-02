package com.youtoo
package cqrs

trait MetaInfo[Event] {
  extension (self: Event) def namespace: Namespace
  extension (self: Event) def hierarchy: Option[Hierarchy]
  extension (self: Event) def props: zio.Chunk[EventProperty]
  extension (self: Event) def reference: Option[ReferenceKey]

}

object MetaInfo {}

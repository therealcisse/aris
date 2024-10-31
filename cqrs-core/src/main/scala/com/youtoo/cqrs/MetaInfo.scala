package com.youtoo
package cqrs

trait MetaInfo[Event] {
  extension (self: Event) def namespace: Namespace

  extension (self: Event) def hierarchy: Option[Hierarchy]

}

object MetaInfo {}

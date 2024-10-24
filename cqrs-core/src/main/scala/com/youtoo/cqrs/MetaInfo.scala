package com.youtoo
package cqrs

trait MetaInfo[Event] {
  extension (self: Event) def namespace: Namespace
}

object MetaInfo {}

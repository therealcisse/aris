package com.youtoo.cqrs

trait MetaInfo[Event] {
  extension (self: Event) def namespace: Namespace
}

object MetaInfo {}

package com.youtoo.cqrs
package domain

import zio.schema.codec.*

import cats.Order

case class Change[Event: BinaryCodec](version: Version, payload: Event)

object Change {
  import zio.schema.*

  given [Event: BinaryCodec: Schema]: Schema[Change[Event]] =
    Schema.CaseClass2[Version, Event, Change[Event]](
      id0 = TypeId.fromTypeName("Change"),
      field01 = Schema.Field(
        name0 = "version",
        schema0 = Schema[Version],
        get0 = _.version,
        set0 = (c, x) => c.copy(version = x),
      ),
      field02 = Schema.Field(
        name0 = "payload",
        schema0 = Schema[Event],
        get0 = _.payload,
        set0 = (c, x) => c.copy(payload = x),
      ),
      construct0 = (version, payload) => Change(version, payload),
    )

  given Order[Change[?]] = Order.by(_.version)
}

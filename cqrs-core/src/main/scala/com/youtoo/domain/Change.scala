package com.youtoo.cqrs
package domain

import zio.schema.codec.*

case class Change[Event: BinaryCodec](payload: Event, timestamp: Timestamp)

object Change {
  import zio.schema.*

  given [Event: BinaryCodec: Schema]: Schema[Change[Event]] =
    Schema.CaseClass2[Event, Timestamp, Change[Event]](
      id0 = TypeId.fromTypeName("Change"),
      field01 = Schema.Field(
        name0 = "payload",
        schema0 = Schema[Event],
        get0 = _.payload,
        set0 = (c, x) => c.copy(payload = x),
      ),
      field02 = Schema.Field(
        name0 = "timestamp",
        schema0 = Schema[Timestamp],
        get0 = _.timestamp,
        set0 = (c, timestamp) => c.copy(timestamp = timestamp),
      ),
      construct0 = (payload, timestamp) => Change(payload, timestamp),
    )

}

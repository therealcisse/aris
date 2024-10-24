package com.youtoo
package cqrs

import zio.schema.*
import zio.schema.codec.*

object Codecs {
  // inline given [T: Schema]: BinaryCodec[T] = ProtobufCodec.protobufCodec

  export protobuf.*

  object protobuf {
    export zio.schema.codec.ProtobufCodec.protobufCodec

  }

  object json {

    given [A: Schema]: BinaryCodec[A] = JsonCodec.schemaBasedBinaryCodec[A](JsonCodec.Config.default)

  }
}

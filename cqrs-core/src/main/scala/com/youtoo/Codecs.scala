package com.youtoo.cqrs

import zio.*
import zio.schema.codec.*
import zio.schema.*

object Codecs {
  // inline given [T: Schema]: BinaryCodec[T] = ProtobufCodec.protobufCodec

  export zio.schema.codec.ProtobufCodec.protobufCodec

}

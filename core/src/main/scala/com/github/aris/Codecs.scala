package com.github
package aris

import zio.schema.*
import zio.schema.codec.*

object Codecs {
  export protobuf.*

  object protobuf {
    export zio.schema.codec.ProtobufCodec.protobufCodec

  }

  object json {

    given [A: Schema] => BinaryCodec[A] = JsonCodec.schemaBasedBinaryCodec[A](JsonCodec.Config.default)

  }
}

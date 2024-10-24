package com.youtoo
package ingestion

import zio.*
import zio.http.*

import zio.schema.codec.*

extension (body: Body)
  inline def fromBody[A: BinaryCodec: Tag]: Task[A] =
    for {
      ch <- body.asChunk
      a <- ZIO.fromEither {
        summon[BinaryCodec[A]].decode(ch)

      }.tapErrorCause { e =>
        ZIO.logErrorCause(s"Error decoding entity ${Tag[A]}", e)
      }

    } yield a

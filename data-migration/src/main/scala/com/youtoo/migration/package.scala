package com.youtoo
package migration

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

inline def boundary[R, E](tag: String)(effect: ZIO[R, E, Response]): ZIO[R, Nothing, Response] =
  effect.catchAllCause { e =>
    ZIO.logErrorCause(s"- [$tag] - Found error", e) `as` Response.notFound
  }

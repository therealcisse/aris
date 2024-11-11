package com.youtoo
package migration

import zio.telemetry.opentelemetry.tracing.*
import zio.telemetry.opentelemetry.baggage.*
import zio.telemetry.opentelemetry.context.*
import io.opentelemetry.api.common.{AttributeKey, Attributes}

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

inline def boundary[R, E](tag: String, request: Request)(
  body: ZIO[R, E, Response],
): URIO[R & Baggage & Tracing, Response] =
  ZIO.serviceWithZIO[Tracing] { (tracing: Tracing) =>

    val effect = body @@ tracing.aspects.root(
      tag,
      attributes = Attributes.of(AttributeKey.stringKey("git_commit_hash"), YouToo.gitCommitHash),
    )

    effect.catchAllCause {
      _.failureOrCause.fold(
        { case e =>
          Log.error(s"[$tag] - Found error", e) `as` Response.internalServerError

        },
        Exit.failCause,
      )

    }

  }

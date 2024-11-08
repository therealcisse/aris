package com.youtoo

import zio.{Runtime, ZIO, UIO, Cause}
import zio.logging.*
import zio.logging.backend.*

object Log {

  inline def layer = Runtime.removeDefaultLoggers >>> SLF4J.slf4j >>> logMetrics

  val GitCommitHashAnnotation: LogAnnotation[String] = LogAnnotation[String](
    name = "git_commit_hash",
    combine = (_, r) => r,
    render = identity
  )

  inline def info(message: => String): UIO[Unit] = ZIO.logInfo(message) @@ GitCommitHashAnnotation(YouToo.gitCommitHash)
  inline def debug(message: => String): UIO[Unit] = ZIO.logDebug(message) @@ GitCommitHashAnnotation(YouToo.gitCommitHash)
  inline def debug[E](message: => String, cause: => E): UIO[Unit] = ZIO.logDebugCause(message, Cause.fail(cause)) @@ GitCommitHashAnnotation(YouToo.gitCommitHash)
  inline def error(message: => String): UIO[Unit] = ZIO.logError(message) @@ GitCommitHashAnnotation(YouToo.gitCommitHash)
  inline def error[E](message: => String, cause: => E): UIO[Unit] = ZIO.logErrorCause(message, Cause.fail(cause)) @@ GitCommitHashAnnotation(YouToo.gitCommitHash)
}

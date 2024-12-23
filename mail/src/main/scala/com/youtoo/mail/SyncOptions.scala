package com.youtoo
package mail

import zio.*

case class SyncOptions(
  maxIterations: Option[Int],
  maxDuration: Option[Duration],
  retry: SyncOptions.Retry,
)

object SyncOptions {

  case class Retry(times: Option[Long], interval: Option[Duration])

  val retryConfig: Config[Retry] =
    (
      Config.long("mail-sync-retry-max-times").optional.mapOrFail {
        case Some(value) if value < 0 =>
          Left(Config.Error.InvalidData(Chunk.empty, s"Expected a positive int, but found ${value}"))
        case v => Right(v)
      } zip
        Config.duration("mail-sync-retry-interval").optional.mapOrFail {
          case Some(value) if value.toMillis < 0 =>
            Left(Config.Error.InvalidData(Chunk.empty, s"Expected a positive duration, but found ${value}"))
          case v => Right(v)
        }
    ).map { case (times, interval) =>
      Retry(times, interval)
    }

  given Config[SyncOptions] =
    (
      Config.int("mail-sync-max-iterations").optional.mapOrFail {
        case Some(value) if value < 0 =>
          Left(Config.Error.InvalidData(Chunk.empty, s"Expected a positive int, but found ${value}"))
        case v => Right(v)
      } zip
        Config.duration("mail-sync-max-duration").optional.mapOrFail {
          case Some(value) if value.toMillis < 0 =>
            Left(Config.Error.InvalidData(Chunk.empty, s"Expected a positive duration, but found ${value}"))
          case v => Right(v)
        } zip
        retryConfig
    ).map { case (maxIterations, maxDuration, retry) =>
      SyncOptions(maxIterations, maxDuration, retry)
    }

  extension (options: SyncOptions)
    inline def retry[R, E, A](o: ZIO[R, E, A]): ZIO[R, E, A] =
      (options.retry.times, options.retry.interval) match {
        case (None, None) => o
        case (Some(n), None) => o.retry(Schedule.recurs(n))
        case (Some(n), Some(i)) => o.retry(Schedule.spaced(i) && Schedule.recurs(n))
        case (None, Some(i)) => o.retry(Schedule.spaced(i))
      }

}

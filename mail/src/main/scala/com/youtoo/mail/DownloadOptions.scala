package com.youtoo
package mail

import zio.*

import zio.telemetry.opentelemetry.tracing.Tracing

import java.time.temporal.ChronoUnit

case class DownloadOptions(
  maxIterations: Option[Int],
  maxDuration: Option[Duration],
  timeout: Duration,
  retry: DownloadOptions.Retry,
)

object DownloadOptions {

  case class Retry(times: Option[Long], interval: Option[Duration])

  val retryConfig: Config[Retry] =
    (
      Config.long("mail_download_retry_max_times").optional.mapOrFail {
        case Some(value) if value < 0 =>
          Left(Config.Error.InvalidData(Chunk.empty, s"Expected a positive int, but found ${value}"))
        case v => Right(v)
      } zip
        Config.duration("mail_download_retry_interval").optional.mapOrFail {
          case Some(value) if value.toMillis < 0 =>
            Left(Config.Error.InvalidData(Chunk.empty, s"Expected a positive duration, but found ${value}"))
          case v => Right(v)
        }
    ).map { case (times, interval) =>
      Retry(times, interval)
    }

  given Config[DownloadOptions] =
    (
      Config.int("mail_download_max_iterations").optional.mapOrFail {
        case Some(value) if value < 0 =>
          Left(Config.Error.InvalidData(Chunk.empty, s"Expected a positive int, but found ${value}"))
        case v => Right(v)
      } zip
        Config.duration("mail_download_max_duration").optional.mapOrFail {
          case Some(value) if value.toMillis < 0 =>
            Left(Config.Error.InvalidData(Chunk.empty, s"Expected a positive duration, but found ${value}"))
          case v => Right(v)
        } zip
        Config.duration("mail_download_fetch_timeout").withDefault(Duration(30L, ChronoUnit.SECONDS)).mapOrFail {
          case value if value.toMillis < 0 =>
            Left(Config.Error.InvalidData(Chunk.empty, s"Expected a positive duration, but found ${value}"))
          case v => Right(v)
        } zip
        retryConfig
    ).map { case (maxIterations, maxDuration, timeout, retry) =>
      DownloadOptions(maxIterations, maxDuration, timeout, retry)
    }

  extension (options: DownloadOptions)
    inline def applyZIO[R, E, A](o: ZIO[R, E, A]): ZIO[R & Tracing, E | IllegalStateException, A] =
      val zo = o.timeoutFail(new IllegalStateException("timout"))(options.timeout)

      (options.retry.times, options.retry.interval) match {
        case (None, None) => zo
        case (Some(n), None) => zo.retry(Schedule.recurs(n) tapOutput (n => Log.debug(s"retrying sync options: $n")))
        case (Some(n), Some(i)) =>
          zo.retry(
            (Schedule.fibonacci(i) && Schedule.recurs(n)) tapOutput (n => Log.debug(s"retrying sync options: $n")),
          )
        case (None, Some(i)) => zo.retry(Schedule.fibonacci(i) tapOutput (n => Log.debug(s"retrying sync options: $n")))
      }

}

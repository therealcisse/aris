package com.youtoo
package std

import cats.implicits.*

import zio.telemetry.opentelemetry.tracing.Tracing

import zio.*
import zio.prelude.*

trait Healthcheck {
  def start(id: Key, interval: Schedule[Any, Any, Any]): RIO[Scope, Unit]
  def getHeartbeat(id: Key): UIO[Option[Timestamp]]
  def isRunning(id: Key): UIO[Boolean]
}

object Healthcheck {
  inline def start(id: Key, interval: Schedule[Any, Any, Any]): RIO[Healthcheck & Scope, Unit] =
    ZIO.serviceWithZIO[Healthcheck](_.start(id, interval))

  inline def getHeartbeat(id: Key): URIO[Healthcheck, Option[Timestamp]] =
    ZIO.serviceWithZIO(_.getHeartbeat(id))

  inline def isRunning(id: Key): URIO[Healthcheck, Boolean] =
    ZIO.serviceWithZIO(_.isRunning(id))

  def live(): ZLayer[Tracing, Throwable, Healthcheck] =
    ZLayer.fromFunction { (tracing: Tracing) =>
      ZLayer {
        Ref.Synchronized.make(Map.empty[Key, (Timestamp, Fiber[Nothing, Any])]) map { ref =>
          new HealthcheckLive(ref).traced(tracing)

        }

      }

    }.flatten

  class HealthcheckLive(ref: Ref.Synchronized[Map[Key, (Timestamp, Fiber[Nothing, Any])]]) extends Healthcheck { self =>
    def start(id: Key, interval: Schedule[Any, Any, Any]): RIO[Scope, Unit] =

      def update(id: Key): UIO[Fiber[Nothing, Any]] =
        (Timestamp.now flatMap { case (t) =>
          ZIO.uninterruptible {
            ref.update { s =>
              s.get(id) match {
                case None => s
                case Some((_, fiber)) => s + (id -> (t -> fiber))

              }
            }

          }
        }).repeat(interval tapOutput (i => ZIO.logDebug(s"Updated heartbeat $i"))).forkDaemon

      val stop = Log.info(s"Stopping health check watch") *> (ref.modifyZIO { s =>
        s.get(id) match {
          case None => ZIO.succeed((ZIO.unit, s))
          case Some((_, fiber)) => ZIO.succeed((fiber.interrupt.unit) -> (s - id))

        }

      }).flatten

      Log.info(s"Start health check watch") *> ZIO.uninterruptibleMask { restore =>
        (ref.updateZIO { s =>
          s.get(id) match {
            case None =>
              (Timestamp.now <&> restore(update(id))) map { case (t, fiber) =>
                (
                  (s + (id -> (t -> fiber)))
                )
              }

            case _ =>
              ZIO.succeed(s)
          }

        }) <* ZIO.addFinalizer(stop)

      }

    def getHeartbeat(id: Key): UIO[Option[Timestamp]] =
      ref.get.map { s =>
        (s.get(id) match {
          case None => None
          case Some((t, _)) => t.some

        })
      }

    def isRunning(id: Key): UIO[Boolean] =
      ref.get.map(_.contains(id))

    inline def traced(tracing: Tracing): Healthcheck =
      new Healthcheck {
        def start(id: Key, interval: Schedule[Any, Any, Any]): RIO[Scope, Unit] =
          self.start(id, interval) @@ tracing.aspects.span("Healthcheck.start")
        def getHeartbeat(id: Key): UIO[Option[Timestamp]] =
          self.getHeartbeat(id) @@ tracing.aspects.span("Healthcheck.getHeartbeat")
        def isRunning(id: Key): UIO[Boolean] = self.isRunning(id) @@ tracing.aspects.span("Healthcheck.isRunning")

      }

  }

}

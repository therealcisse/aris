package com.youtoo
package std

import cats.implicits.*

import zio.*
import zio.prelude.*

trait Healthcheck {

  def start(id: Key, interval: Schedule[Any, Any, Any]): Task[Healthcheck.Handle]

  def getHeartbeat(id: Key): UIO[Option[Timestamp]]

  def isRunning(id: Key): UIO[Boolean]
}

object Healthcheck {
  type Handle = Handle.Type

  object Handle extends Newtype[UIO[Unit]] {
    extension (h: Handle) inline def stop: UIO[Unit] = Handle.unwrap(h)

  }

  inline def start(id: Key, interval: Schedule[Any, Any, Any]): RIO[Healthcheck, Healthcheck.Handle] =
    ZIO.serviceWithZIO(_.start(id, interval))

  inline def getHeartbeat(id: Key): URIO[Healthcheck, Option[Timestamp]] =
    ZIO.serviceWithZIO(_.getHeartbeat(id))

  inline def isRunning(id: Key): URIO[Healthcheck, Boolean] =
    ZIO.serviceWithZIO(_.isRunning(id))

  def live(): ZLayer[Any, Throwable, Healthcheck] =
    ZLayer {
      Ref.Synchronized.make(Map.empty[Key, (Timestamp, Fiber[Nothing, Any])]) map { ref =>
        Live(ref)

      }

    }

  class Live(ref: Ref.Synchronized[Map[Key, (Timestamp, Fiber[Nothing, Any])]]) extends Healthcheck {
    def start(id: Key, interval: Schedule[Any, Any, Any]): Task[Healthcheck.Handle] =

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

      val stop = ZIO.logInfo(s"Stopping health check watch") *> (ref.modifyZIO { s =>
        s.get(id) match {
          case None => ZIO.succeed((ZIO.unit, s))
          case Some((_, fiber)) => ZIO.succeed(fiber.interrupt.unit -> (s - id))

        }

      }).flatten

      ZIO.logInfo(s"Start health check watch") *> ZIO.uninterruptibleMask { restore =>
        (ref.modifyZIO { s =>
          s.get(id) match {
            case None =>
              (Timestamp.now <&> restore(update(id))) map { case (t, fiber) =>
                (
                  Handle(stop),
                  (s + (id -> (t -> fiber))),
                )
              }

            case _ =>
              ZIO.succeed((Handle(stop) -> s))
          }

        })

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

  }

}

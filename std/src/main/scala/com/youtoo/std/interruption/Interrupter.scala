package com.youtoo
package std
package interruption

import zio.*

import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.common.*

trait Interrupter {
  def watch[R](id: Key): ZIO[R & Scope, Throwable, Promise[Throwable, Unit]]
  def interrupt(id: Key): Task[Unit]
}

object Interrupter {
  inline def watch(id: Key): RIO[Interrupter & Scope, Promise[Throwable, Unit]] =
    ZIO.serviceWithZIO[Interrupter](_.watch(id))

  inline def interrupt(id: Key): RIO[Interrupter, Unit] =
    ZIO.serviceWithZIO(_.interrupt(id))

  def live(): ZLayer[Tracing, Throwable, Interrupter] =
    ZLayer.fromFunction { (tracing: Tracing) =>
      ZLayer {

        Ref.Synchronized.make(Map.empty[Key, Promise[Throwable, Unit]]) map { ref =>
          new InterrupterLive(ref).traced(tracing)

        }

      }

    }.flatten

  class InterrupterLive(ref: Ref.Synchronized[Map[Key, Promise[Throwable, Unit]]]) extends Interrupter { self =>

    def watch[R](id: Key): ZIO[R & Scope, Throwable, Promise[Throwable, Unit]] =
      ZIO.uninterruptible {
        for {
          p <- ref.modifyZIO { s =>
            s.get(id) match {
              case None =>
                Promise.make[Throwable, Unit] map { p =>
                  p -> (s + (id -> p))
                }

              case Some(p) =>
                ZIO.succeed(p -> s)
            }

          }

          _ <- ZIO.addFinalizer(ref.update(_ - id))

        } yield p

      }

    def interrupt(id: Key): Task[Unit] =
      ZIO.uninterruptible {

        ref.getAndUpdateZIO { s =>
          (s.get(id) match {
            case None => ZIO.unit
            case Some(p) => p.succeed(())

          }) as (s - id)
        } as ()
      }

    def traced(tracing: Tracing): Interrupter =
      new Interrupter {
        def watch[R](id: Key): ZIO[R & Scope, Throwable, Promise[Throwable, Unit]] =
          self.watch(id) @@ tracing.aspects.span(
            "Interrupter.watch",
            attributes = Attributes(Attribute.long("id", id.value)),
          )
        def interrupt(id: Key): Task[Unit] = self.interrupt(id) @@ tracing.aspects.span(
          "Interrupter.interrupt",
          attributes = Attributes(Attribute.long("id", id.value)),
        )

      }

  }

}

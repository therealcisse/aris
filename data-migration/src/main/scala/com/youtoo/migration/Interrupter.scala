package com.youtoo
package migration

import zio.*

trait Interrupter {
  def watch[R, A](id: Key)(f: Promise[Throwable, Unit] => ZIO[R, Throwable, A]): ZIO[R, Throwable, A]
  def interrupt(id: Key): Task[Unit]
}

object Interrupter {
  inline def watch(id: Key)(f: Promise[Throwable, Unit] => Task[Unit]): RIO[Interrupter, Unit] =
    ZIO.serviceWithZIO(_.watch(id)(f))

  inline def interrupt(id: Key): RIO[Interrupter, Unit] =
    ZIO.serviceWithZIO(_.interrupt(id))

  def live(): ZLayer[Any, Throwable, Interrupter] =
    ZLayer {
      Ref.Synchronized.make(Map.empty[Key, Promise[Throwable, Unit]]) map { ref =>
        Live(ref)

      }

    }

  class Live(ref: Ref.Synchronized[Map[Key, Promise[Throwable, Unit]]]) extends Interrupter {

    def watch[R, A](id: Key)(f: Promise[Throwable, Unit] => ZIO[R, Throwable, A]): ZIO[R, Throwable, A] =
      ref.modifyZIO { s =>
        s.get(id) match {
          case None =>
            Promise.make[Throwable, Unit] map { p =>
              f(p).ensuring(done(id)) -> (s + (id -> p))
            }

          case Some(p) =>
            ZIO.succeed(f(p).ensuring(done(id)) -> s)
        }

      }.flatten

    def interrupt(id: Key): Task[Unit] =
      ref.getAndUpdateZIO { s =>
        (s.get(id) match {
          case None => ZIO.unit
          case Some(p) => p.succeed(())

        }) as (s - id)
      } as ()

    private def done(id: Key): UIO[Unit] =
      ref.update(_ - id)

  }

}

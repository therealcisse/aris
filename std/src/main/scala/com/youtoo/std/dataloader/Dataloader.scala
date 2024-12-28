package com.youtoo
package std
package dataloader

import cats.implicits.*

import scala.collection.mutable.MultiDict as MultiMap

import zio.*
import java.time.temporal.ChronoUnit

trait Dataloader[T] {
  def fetch(id: Key): Task[Option[T]]

}

object Dataloader {
  inline val INTERVAL = 15L

  case class State[A](backlog: MultiMap[Key, Promise[Throwable, Option[A]]])

  object State {
    inline def empty[A]: State[A] = State(MultiMap.empty[Key, Promise[Throwable, Option[A]]])

    extension [A](s: State[A])
      inline def addRequest(id: Key, promise: Promise[Throwable, Option[A]]): State[A] =
        s.backlog += (id -> promise)
        s

    extension [A](s: State[A])
      inline def removeRequest(id: Key, promise: Promise[Throwable, Option[A]]): State[A] =
        s.backlog -= (id -> promise)
        s

    extension [A](s: State[A])
      inline def getRequest(batchSize: Int): Iterable[Key] = s.backlog.sets.take(batchSize).keys

    extension [A](s: State[A])
      inline def done(keys: Iterable[Key]): State[A] =
        keys.foreach(key => s.backlog -*= key)
        s

  }

  trait Factory {
    def createLoader[A: Keyed](loader: BulkLoader[A], batchSize: Int): ZIO[Scope, Throwable, Dataloader[A]]

  }

  trait Keyed[A] {
    extension (a: A) def id: Key

  }

  trait BulkLoader[A: Keyed] {
    def loadMany(ids: NonEmptyChunk[Key]): Task[List[A]]
  }

  def live(): ZLayer[Any, Throwable, Dataloader.Factory] =
    ZLayer.succeed {

      new Live()

    }

  class Live() extends Dataloader.Factory {
    def createLoader[A: Keyed](loader: Dataloader.BulkLoader[A], batchSize: Int): ZIO[Scope, Throwable, Dataloader[A]] =
      Ref.Synchronized.make(State.empty[A]) flatMap { ref =>
        def setup: ZIO[Scope, Throwable, ?] =
          for {
            started <- Clock.currentTime(ChronoUnit.MILLIS)

            keys <- ref.get.map(_.getRequest(batchSize))

            response <- NonEmptyChunk.fromIterableOption(keys) match {
              case None => ZIO.succeed(Map.empty[Key, A])
              case Some(nec) => loader.loadMany(nec).map(_.map(e => (e.id, e)).toMap)
            }

            _ <- ref.get.flatMap { s =>
              ZIO.foreachParDiscard(keys) { case (key) =>
                val value = response.get(key)

                ZIO.collectAllParDiscard {

                  s.backlog.get(key) map (
                    _.succeed(value)
                  )
                }
              }

            }

            _ <- ZIO.uninterruptible(ref.update(_.done(keys)))

            completed <- Clock.currentTime(ChronoUnit.MILLIS)

            wait = INTERVAL - (completed - started)

            _ <- if wait <= 0L then ZIO.unit else ZIO.sleep(wait.millisecond)

            _ <- ZIO.yieldNow

            _ <- setup

          } yield ()

        def cancelled(id: Key, promise: Promise[Throwable, Option[A]]): UIO[Unit] =
          ZIO.uninterruptible(ref.update(_.removeRequest(id, promise)))

        setup.forkScoped `as` new Dataloader[A] {

          def fetch(id: Key): Task[Option[A]] =
            ZIO.uninterruptibleMask { restore =>
              Promise.make[Throwable, Option[A]] flatMap { p =>
                ref.update(_.addRequest(id, p)) *> restore(p.await).onInterrupt(cancelled(id, p))

              }

            }

        }

      }

  }

}

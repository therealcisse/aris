package com.youtoo
package std

import cats.implicits.*

import zio.*
import java.time.temporal.ChronoUnit
import scala.collection.immutable.TreeMap

trait Dataloader[T] {
  def fetch(id: Key): Task[Option[T]]

}

object Dataloader {
  inline val INTERVAL = 15L

  case class State[A](backlog: Map[Key, Promise[Throwable, Option[A]]])

  object State {
    inline def empty[A]: State[A] = State(TreeMap.empty)

    extension [A](s: State[A])
      inline def addRequest(id: Key, promise: Promise[Throwable, Option[A]]): State[A] = State(
        s.backlog + (id -> promise),
      )
    extension [A](s: State[A]) inline def removeRequest(id: Key): State[A] = State(s.backlog - id)
    extension [A](s: State[A]) inline def getRequest(batchSize: Int): Iterable[Key] = s.backlog.take(batchSize).keys
    extension [A](s: State[A]) inline def done(keys: Iterable[Key]): State[A] = State(s.backlog -- keys)
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
            _ <- ZIO.logInfo("Fetch")

            started <- Clock.currentTime(ChronoUnit.MILLIS)

            keys <- ref.get.map(_.getRequest(batchSize))

            response <- NonEmptyChunk.fromIterableOption(keys) match {
              case None => ZIO.succeed(Map.empty[Key, A])
              case Some(nec) => loader.loadMany(nec).map(_.map(e => (e.id, e)).toMap)
            }

            _ <- ZIO.foreachParDiscard(keys) { case (key) =>
              ref.get.flatMap(_.backlog.get(key) match {
                case None => ZIO.logInfo(s"Not found: $key")
                case Some(p) => ZIO.logInfo(s"Complete: $key: ${response.get(key)}") *> p.succeed(response.get(key))
              })

            }

            _ <- ZIO.uninterruptible(ref.update(_.done(keys)))

            completed <- Clock.currentTime(ChronoUnit.MILLIS)

            wait = INTERVAL - (completed - started)
            _ = println(s"Elapsed: ${completed - started}")
            _ = println(s"WAIT: ${wait}")

            _ <- if wait <= 0L then ZIO.unit else ZIO.sleep(wait.millisecond)

            _ <- ZIO.logInfo("Done")

            _ <- ZIO.yieldNow

            _ <- setup

          } yield ()

        def cancelled(id: Key): UIO[Unit] =
          ZIO.logInfo(s"Cancelled $id") *> ZIO.uninterruptible(ref.update(_.removeRequest(id)))

        setup.forkScoped `as` new Dataloader[A] {

          def fetch(id: Key): Task[Option[A]] =
            ZIO.uninterruptibleMask { restore =>
              (ref.modifyZIO { s =>
                s.backlog.get(id) match {
                  case None =>
                    Promise.make[Throwable, Option[A]] map { p =>
                      restore(p.await).onInterrupt(cancelled(id)) -> s.addRequest(id, p)
                    }

                  case Some(p) => ZIO.succeed(restore(p.await) -> s)
                }

              }).flatten

            }

        }

      }

  }

}

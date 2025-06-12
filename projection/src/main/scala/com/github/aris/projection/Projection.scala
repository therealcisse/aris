package com.github
package aris
package projection

import com.github.aris.domain.*

import zio.*
import zio.stream.*
import zio.prelude.*

trait Projection {
  def run: ZIO[Scope, Nothing, Unit]
}

object Projection {
  import zio.prelude.*

  type Name = Name.Type
  object Name extends Newtype[String] {
    extension (n: Name) inline def value: String = unwrap(n)
  }

  type VersionId = VersionId.Type
  object VersionId extends Newtype[String] {
    extension (v: VersionId) inline def value: String = unwrap(v)
  }

  final case class Id(name: Name, version: VersionId, namespace: Namespace)

  def exactlyOnce[Event](
    id: Id,
    handler: Envelope[Event] => Task[Unit],
    query: Version => ZStream[Any, Throwable, Envelope[Event]],
    store: ProjectionManagementStore,
    observer: ProjectionObserver = ProjectionObserver.empty,
    retry: RetryStrategy = RetryStrategy.Skip,
  ): Projection =
    ExactlyOnce(id, handler, query, store, observer, retry)

  def atLeastOnce[Event](
    id: Id,
    handler: Envelope[Event] => Task[Unit],
    query: Version => ZStream[Any, Throwable, Envelope[Event]],
    store: ProjectionManagementStore,
    commitAfterN: Int,
    commitAfter: Duration,
    observer: ProjectionObserver = ProjectionObserver.empty,
    retry: RetryStrategy = RetryStrategy.Skip,
  ): Projection =
    AtLeastOnce(id, handler, query, store, commitAfterN, commitAfter, observer, retry)
}

private[aris] final case class ExactlyOnce[Event](
  id: Projection.Id,
  handler: Envelope[Event] => Task[Unit],
  query: Version => ZStream[Any, Throwable, Envelope[Event]],
  store: ProjectionManagementStore,
  observer: ProjectionObserver = ProjectionObserver.empty,
  retry: RetryStrategy = RetryStrategy.Skip,
) extends Projection {

  def run: ZIO[Scope, Nothing, Unit] =
    store.isPaused(id).flatMap { paused =>
      if paused then ZIO.unit
      else process
    }.orDie

  private def process: ZIO[Scope, Throwable, Unit] =
    for {
      start <- store.offset(id).map(_.getOrElse(Version.wrap(0L))).orDie
      _     <- observer.started(id, start)
      _     <- query(start)
                 .foreach { env =>
                   handleWithRetry(env) *> commit(env.version)
                 }
                 .tapError(e => observer.projectionError(id, e))
    } yield ()

  private def commit(v: Version): Task[Unit] =
    store.updateOffset(id, v) *> observer.offsetCommitted(id, v)

  private def handleWithRetry(env: Envelope[Event]): ZIO[Any, Nothing, Unit] =
    def loop(left: Int): ZIO[Any, Nothing, Unit] =
      handler(env).catchAll { e =>
        observer.processingError(id, env.version, e) *>
          (retry match
            case RetryStrategy.Skip                      => ZIO.unit
            case RetryStrategy.RetryAndFail(n) if left > 0  => loop(left - 1)
            case RetryStrategy.RetryAndFail(_)              => ZIO.unit
            case RetryStrategy.RetryAndSkip(n) if left > 0  => loop(left - 1)
            case RetryStrategy.RetryAndSkip(_)              => ZIO.unit
          )
      }

    retry match
      case RetryStrategy.Skip            => loop(0)
      case RetryStrategy.RetryAndFail(n) => loop(n)
      case RetryStrategy.RetryAndSkip(n) => loop(n)
}

private[aris] final case class AtLeastOnce[Event](
  id: Projection.Id,
  handler: Envelope[Event] => Task[Unit],
  query: Version => ZStream[Any, Throwable, Envelope[Event]],
  store: ProjectionManagementStore,
  commitAfterN: Int,
  commitAfter: Duration,
  observer: ProjectionObserver = ProjectionObserver.empty,
  retry: RetryStrategy = RetryStrategy.Skip,
) extends Projection {

  def run: ZIO[Scope, Nothing, Unit] =
    store.isPaused(id).flatMap { paused =>
      if paused then ZIO.unit
      else process
    }.orDie

  private def process: ZIO[Scope, Throwable, Unit] =
    for {
      start <- store.offset(id).map(_.getOrElse(Version.wrap(0L))).orDie
      _     <- observer.started(id, start)
      ref   <- Ref.make((start, 0, 0L))
      _     <- query(start)
                 .foreach { env =>
                   handleWithRetry(env) *> maybeCommit(env.version, ref)
                 }
                 .tapError(e => observer.projectionError(id, e))
    } yield ()

  private def commit(v: Version): Task[Unit] =
    store.updateOffset(id, v) *> observer.offsetCommitted(id, v)

  private def maybeCommit(v: Version, ref: Ref[(Version, Int, Long)]): Task[Unit] =
    for {
      now    <- Clock.nanoTime
      shouldSave <- ref.modify { case (_, count, ts) =>
                   val newCount   = count + 1
                   val elapsed    = Duration.fromNanos(now - ts)
                   val shouldSave = newCount >= commitAfterN || elapsed >= commitAfter
                   val nextTs     = if shouldSave then now else ts
                   val next       = if shouldSave then (v, 0, nextTs) else (v, newCount, ts)
                   (shouldSave, next)
                 }
      _ <- ZIO.when(shouldSave)(commit(v))
    } yield ()

  private def handleWithRetry(env: Envelope[Event]): ZIO[Any, Nothing, Unit] =
    def loop(left: Int): ZIO[Any, Nothing, Unit] =
      handler(env).catchAll { e =>
        observer.processingError(id, env.version, e) *>
          (retry match
            case RetryStrategy.Skip                      => ZIO.unit
            case RetryStrategy.RetryAndFail(n) if left > 0  => loop(left - 1)
            case RetryStrategy.RetryAndFail(_)              => ZIO.unit
            case RetryStrategy.RetryAndSkip(n) if left > 0  => loop(left - 1)
            case RetryStrategy.RetryAndSkip(_)              => ZIO.unit
          )
      }

    retry match
      case RetryStrategy.Skip            => loop(0)
      case RetryStrategy.RetryAndFail(n) => loop(n)
      case RetryStrategy.RetryAndSkip(n) => loop(n)
}

package com.github
package aris
package projection

import zio.*
import zio.stream.*

trait Projection {
  def run: ZIO[Scope, Throwable, Unit]
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

  final case class CommitOffset(afterN: Option[Int], after: Option[Duration])
  object CommitOffset {
    inline def apply(): CommitOffset = CommitOffset(None, None)
    inline def afterN(n: Int): CommitOffset = CommitOffset(Some(n), None)
    inline def after(d: Duration): CommitOffset = CommitOffset(None, Some(d))

    extension (c: CommitOffset)
      def afterN(n: Int): CommitOffset = c.copy(afterN = Some(n))
      def after(d: Duration): CommitOffset = c.copy(after = Some(d))
  }

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
    commit: CommitOffset,
    store: ProjectionManagementStore,
    observer: ProjectionObserver = ProjectionObserver.empty,
    retry: RetryStrategy = RetryStrategy.Skip,
  ): Projection =
    AtLeastOnce(id, handler, query, store, commit, observer, retry)
}

final private[aris] case class ExactlyOnce[Event](
  id: Projection.Id,
  handler: Envelope[Event] => Task[Unit],
  query: Version => ZStream[Any, Throwable, Envelope[Event]],
  store: ProjectionManagementStore,
  observer: ProjectionObserver = ProjectionObserver.empty,
  retry: RetryStrategy = RetryStrategy.Skip,
) extends Projection {

  def run: ZIO[Scope, Throwable, Unit] =
    store
      .isStopped(id)
      .flatMap { stopped =>
        if stopped then ZIO.unit
        else process
      }

  private def process: ZIO[Scope, Throwable, Unit] =
    for {
      start <- store.offset(id).map(_.getOrElse(Version.wrap(0L)))
      _ <- observer.started(id, start)
      stoppedRef <- Ref.make(false)
      _ <- (store
        .isStopped(id)
        .flatMap(stoppedRef.set))
        .repeat(Schedule.spaced(15.seconds))
        .forkScoped
      _ <- query(start).foreach { env =>
        stoppedRef.get.flatMap { stopped =>
          if stopped then ZIO.unit
          else handleWithRetry(env) *> commit(env.version)
        }
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
            case RetryStrategy.Skip => ZIO.unit
            case RetryStrategy.RetryAndFail(_) if left > 0 => loop(left - 1)
            case RetryStrategy.RetryAndFail(_) => ZIO.unit
            case RetryStrategy.RetryAndSkip(_) if left > 0 => loop(left - 1)
            case RetryStrategy.RetryAndSkip(_) => ZIO.unit
          )
      }

    retry match
      case RetryStrategy.Skip => loop(0)
      case RetryStrategy.RetryAndFail(n) => loop(n)
      case RetryStrategy.RetryAndSkip(n) => loop(n)
}

final private[aris] case class AtLeastOnce[Event](
  id: Projection.Id,
  handler: Envelope[Event] => Task[Unit],
  query: Version => ZStream[Any, Throwable, Envelope[Event]],
  store: ProjectionManagementStore,
  commit: CommitOffset,
  observer: ProjectionObserver = ProjectionObserver.empty,
  retry: RetryStrategy = RetryStrategy.Skip,
) extends Projection {

  def run: ZIO[Scope, Throwable, Unit] =
    store
      .isStopped(id)
      .flatMap { stopped =>
        if stopped then ZIO.unit
        else process
      }

  private def process: ZIO[Scope, Throwable, Unit] =
    for {
      start <- store.offset(id).map(_.getOrElse(Version.wrap(0L)))
      _ <- observer.started(id, start)
      ref <- Ref.make((start, 0, 0L))
      stoppedRef <- Ref.make(false)
      _ <- (store
        .isStopped(id)
        .flatMap(stoppedRef.set))
        .repeat(Schedule.spaced(15.seconds))
        .forkScoped
      _ <- query(start).foreach { env =>
        stoppedRef.get.flatMap { stopped =>
          if stopped then ZIO.unit
          else handleWithRetry(env) *> maybeCommit(env.version, ref)
        }
      }
        .tapError(e => observer.projectionError(id, e))
    } yield ()

  private def commit(v: Version): Task[Unit] =
    store.updateOffset(id, v) *> observer.offsetCommitted(id, v)

  private def maybeCommit(v: Version, ref: Ref[(Version, Int, Long)]): Task[Unit] =
    for {
      now <- Clock.nanoTime
      shouldSave <- ref.modify { case (_, count, ts) =>
        val newCount = count + 1
        val elapsed = Duration.fromNanos(now - ts)
        val nReached = commit.afterN.exists(newCount >= _)
        val tReached = commit.after.exists(elapsed >= _)
        val shouldSave = nReached || tReached
        val nextTs = if shouldSave then now else ts
        val next = if shouldSave then (v, 0, nextTs) else (v, newCount, ts)
        (shouldSave, next)
      }
      _ <- ZIO.when(shouldSave)(commit(v))
    } yield ()

  private def handleWithRetry(env: Envelope[Event]): ZIO[Any, Nothing, Unit] =
    def loop(left: Int): ZIO[Any, Nothing, Unit] =
      handler(env).catchAll { e =>
        observer.processingError(id, env.version, e) *>
          (retry match
            case RetryStrategy.Skip => ZIO.unit
            case RetryStrategy.RetryAndFail(_) if left > 0 => loop(left - 1)
            case RetryStrategy.RetryAndFail(_) => ZIO.unit
            case RetryStrategy.RetryAndSkip(_) if left > 0 => loop(left - 1)
            case RetryStrategy.RetryAndSkip(_) => ZIO.unit
          )
      }

    retry match
      case RetryStrategy.Skip => loop(0)
      case RetryStrategy.RetryAndFail(n) => loop(n)
      case RetryStrategy.RetryAndSkip(n) => loop(n)
}

package com.youtoo
package migration

import zio.*

import zio.stream.*

import com.youtoo.migration.model.*

trait DataMigration {
  def run(id: Migration.Id): ZIO[DataMigration.Processor & MigrationCQRS, Throwable, Unit]
  def stop(id: Migration.Id): Task[Unit]

}

object DataMigration {
  inline val BATCH_SIZE = 4

  inline def count(): RIO[DataMigration.Processor, Long] = ZIO.serviceWithZIO(_.count())
  inline def load(): ZStream[DataMigration.Processor, Throwable, Key] = ZStream.serviceWithStream(_.load())
  inline def process(key: Key): RIO[DataMigration.Processor, Unit] = ZIO.serviceWithZIO(_.process(key))

  inline def run(id: Migration.Id): RIO[DataMigration & MigrationCQRS & DataMigration.Processor, Unit] =
    ZIO.serviceWithZIO[DataMigration](_.run(id))

  inline def stop(id: Migration.Id): RIO[DataMigration, Unit] =
    ZIO.serviceWithZIO(_.stop(id))

  trait Processor {
    def count(): Task[Long]
    def load(): ZStream[Any, Throwable, Key]
    def process(key: Key): Task[Unit]

  }

  case class State(total: Long, keys: Set[Key])

  object State {
    extension (s: State) inline def isProcessed(id: Key): Boolean = s.keys.contains(id)

    extension (s: State) inline def addProcessed(key: Key): State = s.copy(keys = s.keys + key)
    extension (s: State) inline def addFailed(key: Key): State = s.copy(keys = s.keys + key)

  }

  def live(): ZLayer[Interrupter, Throwable, DataMigration] =
    ZLayer.fromFunction { (interrupter: Interrupter) =>
      new DataMigration.Live(interrupter, batchSize = BATCH_SIZE)
    }

  class Live(interrupter: Interrupter, batchSize: Int) extends DataMigration {
    def run(id: Migration.Id): ZIO[DataMigration.Processor & MigrationCQRS, Throwable, Unit] =
      val migrationKey = id.asKey

      inline def logInfo(msg: => String): Task[Unit] =
        ZIO.logInfo(msg) @@ ZIOAspect.annotated("migration_id", migrationKey.value)
      inline def logError(msg: => String, e: => Throwable): Task[Unit] =
        ZIO.logErrorCause(msg, Cause.die(e)) @@ ZIOAspect.annotated("migration_id", migrationKey.value)

      (DataMigration.count() <&> MigrationCQRS.load(id = migrationKey)) flatMap {
        case (_, None) => ZIO.fail(IllegalArgumentException("Migration not found"))

        case (l, Some(migration)) =>
          val remaining = l - migration.totalProcessed

          if remaining < 0 then
            logInfo(s"Invalid remaining keys for migration: $remaining / $l") *> ZIO.fail(
              IllegalStateException("Migration already processed"),
            )
          else
            interrupter.watch(migrationKey) { p =>
              for {
                executionId <- ((Execution.Id.gen <&> Timestamp.now) flatMap ((executionId, timestamp) =>
                  MigrationCQRS.add(
                    id = migrationKey,
                    cmd = MigrationCommand.StartExecution(id = executionId, timestamp),
                  ) `as` executionId
                ))

                ref <- Ref.Synchronized.make(State(remaining, keys = migration.keys))

                op = DataMigration
                  .load()
                  .interruptWhen(p)
                  .mapZIOParUnordered(n = batchSize) { key =>
                    ref.getAndUpdateZIO { s =>
                      if s.isProcessed(key) then ZIO.succeed(s)
                      else
                        DataMigration.process(key) foldZIO (
                          success = _ =>
                            logInfo(s"Processed key: $key") *> MigrationCQRS.add(
                              id = migrationKey,
                              cmd = MigrationCommand.ProcessKey(id = executionId, key),
                            ) `as` s.addProcessed(key),
                          failure = e =>
                            logError(s"Processing failed: $key", e) *> MigrationCQRS.add(
                              id = migrationKey,
                              cmd = MigrationCommand.FailKey(id = executionId, key),
                            ) `as` s.addFailed(key)
                        )

                    }

                  }
                  .runDrain

                timestamp <- Timestamp.now

                cmd <- op foldZIO (
                  success = _ =>
                    for {
                      isInterrupted <- p.isDone

                      cmd <-
                        if isInterrupted then
                          logInfo(s"Migration stopped") `as` MigrationCommand.StopExecution(id = executionId, timestamp)
                        else
                          logInfo(s"Migration succeeded") `as` MigrationCommand.FinishExecution(
                            id = executionId,
                            timestamp,
                          )
                    } yield cmd,
                  failure = e =>
                    logError(s"Migration failed", e) `as` MigrationCommand.FailExecution(id = executionId, timestamp)
                )

                _ <- MigrationCQRS.add(id = migrationKey, cmd = cmd)

              } yield ()

            }

      }

    def stop(id: Migration.Id): Task[Unit] = interrupter.interrupt(id.asKey)

  }
}

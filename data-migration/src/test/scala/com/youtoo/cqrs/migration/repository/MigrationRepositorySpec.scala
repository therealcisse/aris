package com.youtoo.cqrs
package migration
package repository

import com.youtoo.cqrs.migration.model.*
import com.youtoo.cqrs.service.postgres.*
import com.youtoo.cqrs.config.*
import com.youtoo.cqrs.service.*

import zio.*
import zio.prelude.*
import zio.test.*
import zio.test.Assertion.*
import zio.jdbc.*

object MigrationRepositorySpec extends PgSpec {

  def spec: Spec[ZConnectionPool & DatabaseConfig & FlywayMigration & TestEnvironment & Scope, Any] =
    suite("MigrationRepositorySpec")(
      test("load migration is optimized") {
        check(migrationIdGen) { case (id) =>
          val query = MigrationRepository.Queries.READ_MIGRATION(id)
          for {

            executionTime <- atomically(query.selectAll).timed.map(_._1)
            timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

            executionPlan <- atomically(query.sql.getExecutionPlan)
            planAssertion = assert(executionPlan)(containsString("Index Scan") || containsString("Index Only Scan"))

          } yield planAssertion && timeAssertion
        }
      },
      test("load many migrations is optimized") {
        check(Gen.option(keyGen), Gen.long(100, 10_000)) { case (offset, limit) =>
          val query = MigrationRepository.Queries.READ_MIGRATIONS(offset, limit)
          for {

            executionTime <- atomically(query.selectAll).timed.map(_._1)
            timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

            executionPlan <- atomically(query.sql.getExecutionPlan)
            planAssertion = assert(executionPlan)(containsString("Index Scan") || containsString("Index Only Scan"))

          } yield planAssertion && timeAssertion
        }
      },
      test("should save and load an migration") {
        check(migrationGen) { migration =>
          atomically {
            for {

              _ <- MigrationRepository.save(migration)
              result <- MigrationRepository.load(migration.id)
            } yield assert(result)(isSome(equalTo(migration)))
          }
        }
      } @@ TestAspect.samples(1),
      test("should load multiple migrations with loadMany") {
        check(migrationGen, migrationGen) { case (migration1, migration2) =>
          atomically {

            for {
              _ <- MigrationRepository.save(migration1)
              _ <- MigrationRepository.save(migration2)
              keys <- MigrationRepository.loadMany(None, 10)
            } yield assert(keys)(contains(migration1.id.asKey) && contains(migration2.id.asKey))
          }
        }
      } @@ TestAspect.samples(1),
      test("should update an existing migration") {
        check(migrationGen, validMigrationEventSequence) { case (migration, changes) =>
          atomically {

            for {
              _ <- MigrationRepository.save(migration)
              updatedMigration = changes.peelNonEmpty match {
                case (_, Some(nes)) => summon[MigrationEventHandler].applyEvents(migration, nes)
                case _ => migration
              }
              _ <- MigrationRepository.save(updatedMigration)
              result <- MigrationRepository.load(migration.id)
            } yield assert(result)(isSome(equalTo(updatedMigration)))
          }
        }
      } @@ TestAspect.samples(1),
      test("load should return None for non-existent migration") {
        atomically {

          for {
            id <- Random.nextUUID.map(uuid => Migration.Id(Key(uuid.toString)))
            result <- MigrationRepository.load(id)
          } yield assert(result)(isNone)
        }
      } @@ TestAspect.samples(1),
    ).provideSomeLayerShared(
      MigrationRepository.live(),
    ) @@ TestAspect.sequential @@ TestAspect.withLiveClock @@ TestAspect.beforeAll {
      for {
        config <- ZIO.service[DatabaseConfig]
        _ <- FlywayMigration.run(config)

      } yield ()

    }
}

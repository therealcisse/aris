package com.youtoo
package ingestion
package repository

import com.youtoo.postgres.*
import com.youtoo.cqrs.*
import com.youtoo.ingestion.model.*
import com.youtoo.cqrs.service.postgres.*
import com.youtoo.postgres.config.*
import com.youtoo.cqrs.service.*

import zio.*
import zio.prelude.*
import zio.test.*
import zio.test.Assertion.*
import zio.jdbc.*

object IngestionRepositorySpec extends PgSpec, TestSupport {

  def spec: Spec[ZConnectionPool & DatabaseConfig & FlywayMigration & TestEnvironment & Scope, Any] =
    suite("IngestionRepositorySpec")(
      test("load ingestion is optimized") {
        check(ingestionIdGen) { case (id) =>
          val query = IngestionRepository.Queries.READ_INGESTION(id)
          for {

            executionTime <- atomically(query.selectAll).timed.map(_._1)
            timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

            executionPlan <- atomically(query.sql.getExecutionPlan)
            planAssertion = assert(executionPlan)(containsString("Index Scan") || containsString("Index Only Scan"))

          } yield planAssertion && timeAssertion
        }
      },
      test("load many ingestions is optimized") {
        check(Gen.option(keyGen), Gen.long(100, 10_000)) { case (offset, limit) =>
          val query = IngestionRepository.Queries.READ_INGESTIONS(offset, limit)
          for {

            executionTime <- atomically(query.selectAll).timed.map(_._1)
            timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

            executionPlan <- atomically(query.sql.getExecutionPlan)
            planAssertion = assert(executionPlan)(containsString("Index Scan") || containsString("Index Only Scan"))

          } yield planAssertion && timeAssertion
        }
      },
      test("should save and load an ingestion") {
        check(ingestionGen) { ingestion =>
          atomically {
            for {

              _ <- IngestionRepository.save(ingestion)
              result <- IngestionRepository.load(ingestion.id)
            } yield assert(result)(isSome(equalTo(ingestion)))
          }
        }
      } @@ TestAspect.samples(1),
      test("should load multiple ingestions with loadMany") {
        check(ingestionGen, ingestionGen) { case (ingestion1, ingestion2) =>
          atomically {

            for {
              _ <- IngestionRepository.save(ingestion1)
              _ <- IngestionRepository.save(ingestion2)
              keys <- IngestionRepository.loadMany(None, 10)
            } yield assert(keys)(contains(ingestion1.id.asKey) && contains(ingestion2.id.asKey))
          }
        }
      } @@ TestAspect.samples(1),
      test("should update an existing ingestion") {
        check(ingestionGen) { ingestion =>
          atomically {

            for {
              _ <- IngestionRepository.save(ingestion)
              updatedIngestion = ingestion.copy(status =
                Ingestion.Status.Completed(NonEmptySet(IngestionFile.Id(1L), IngestionFile.Id(2L))),
              )
              _ <- IngestionRepository.save(updatedIngestion)
              result <- IngestionRepository.load(ingestion.id)
            } yield assert(result)(isSome(equalTo(updatedIngestion)))
          }
        }
      } @@ TestAspect.samples(1),
      test("load should return None for non-existent ingestion") {
        atomically {

          for {
            id <- Random.nextLong.map(l => Ingestion.Id(Key(l)))
            result <- IngestionRepository.load(id)
          } yield assert(result)(isNone)
        }
      } @@ TestAspect.samples(1),
    ).provideSomeLayerShared(
      ZLayer.make[IngestionRepository](
        IngestionRepository.live(),
        (zio.telemetry.opentelemetry.OpenTelemetry.contextZIO >>> tracingMockLayer()),
      ),
    ) @@ TestAspect.withLiveClock @@ TestAspect.beforeAll {
      for {
        config <- ZIO.service[DatabaseConfig]
        _ <- FlywayMigration.run(config)

      } yield ()

    }
}

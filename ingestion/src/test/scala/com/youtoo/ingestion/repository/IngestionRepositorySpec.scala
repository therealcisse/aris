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
import zio.test.*
import zio.test.TestAspect.*
import zio.test.Assertion.*
import zio.jdbc.*

object IngestionRepositorySpec extends PgSpec, TestSupport {

  def spec: Spec[ZConnectionPool & DatabaseConfig & FlywayMigration & TestEnvironment & Scope, Any] =
    suite("IngestionRepositorySpec")(
      loadIngestionIsOptimized,
      loadManyIngestionsIsOptimized,
      shouldSaveAndLoadAnIngestion,
      shouldLoadMultipleIngestionsWithLoadMany,
      loadShouldReturnNoneForNonExistentIngestion,
    ).provideSomeLayerShared(
      ZLayer.make[IngestionRepository](
        IngestionRepository.live(),
        (zio.telemetry.opentelemetry.OpenTelemetry.contextZIO >>> tracingMockLayer()),
      ),
    ) @@ nondeterministic @@ samples(1) @@ beforeAll {
      for {
        config <- ZIO.service[DatabaseConfig]
        _ <- FlywayMigration.run(config)

      } yield ()

    }

  def loadIngestionIsOptimized =
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
    }

  def loadManyIngestionsIsOptimized =
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
    }

  def shouldSaveAndLoadAnIngestion =
    test("should save and load an ingestion") {
      check(ingestionGen) { ingestion =>
        atomically {
          for {
            rs <- IngestionRepository.loadMany(None, 100L)

            _ <- IngestionRepository.save(ingestion)
            result <- IngestionRepository.load(ingestion.id)
          } yield assert(result)(equalTo(Some(ingestion)))
        }
      }
    }

  def shouldLoadMultipleIngestionsWithLoadMany =
    test("should load multiple ingestions with loadMany") {
      check(ingestionGen <*> ingestionGen) { case (ingestion1, ingestion2) =>
        atomically {

          for {
            _ <- IngestionRepository.save(ingestion1)
            _ <- IngestionRepository.save(ingestion2)
            keys <- IngestionRepository.loadMany(None, 10)
          } yield assert(keys)(contains(ingestion1.id.asKey) && contains(ingestion2.id.asKey))
        }
      }
    }

  def loadShouldReturnNoneForNonExistentIngestion =
    test("load should return None for non-existent ingestion") {
      atomically {

        for {
          id <- Random.nextLong.map(l => Ingestion.Id(Key(l)))
          result <- IngestionRepository.load(id)
        } yield assert(result)(isNone)
      }
    }

}

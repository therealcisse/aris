package com.youtoo
package job
package repository

import com.youtoo.postgres.config.*
import com.youtoo.postgres.*
import com.youtoo.job.model.*
import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.jdbc.*

object JobRepositorySpec extends PgSpec, TestSupport {

  def spec: Spec[ZConnectionPool & DatabaseConfig & FlywayMigration & TestEnvironment & Scope, Any] =
    suite("JobRepositorySpec")(
      test("load job is optimized") {
        check(jobIdGen) { case (id) =>
          val query = JobRepository.Queries.READ_JOB(id)
          for {
            executionTime <- atomically(query.selectAll).timed.map(_._1)
            timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

            executionPlan <- atomically(query.sql.getExecutionPlan)
            planAssertion = assert(executionPlan)(containsString("Index Scan") || containsString("Index Only Scan"))
          } yield planAssertion && timeAssertion
        }
      },
      test("should save and load a job") {
        check(jobGen) { job =>
          atomically {
            for {
              _ <- JobRepository.save(job)
              result <- JobRepository.load(job.id)
            } yield assert(result)(isSome(equalTo(job)))
          }
        }
      } @@ TestAspect.samples(1),
      test("should load multiple jobs with loadMany") {
        check(jobGen, jobGen) { case (job1, job2) =>
          atomically {
            for {
              _ <- JobRepository.save(job1)
              _ <- JobRepository.save(job2)
              keys <- JobRepository.loadMany(None, 10)
            } yield assert(keys)(contains(job1.id.asKey) && contains(job2.id.asKey))
          }
        }
      } @@ TestAspect.samples(1),
      test("should update an existing job") {
        check(jobGen, jobStatusGen) { (job, status) =>
          atomically {
            for {
              _ <- JobRepository.save(job)
              updatedJob = job.copy(status = status)
              _ <- JobRepository.save(updatedJob)
              result <- JobRepository.load(job.id)
            } yield assert(result)(isSome(equalTo(updatedJob)))
          }
        }
      } @@ TestAspect.samples(1),
      test("load should return None for non-existent job") {
        atomically {
          for {
            id <- Random.nextLong.map(l => Job.Id(Key(l)))
            result <- JobRepository.load(id)
          } yield assert(result)(isNone)
        }
      } @@ TestAspect.samples(1),
    ).provideSomeLayerShared(
      ZLayer.make[JobRepository](
        JobRepository.live(),
        (zio.telemetry.opentelemetry.OpenTelemetry.contextZIO >>> tracingMockLayer()),
      ),
    ) @@ TestAspect.withLiveClock @@ TestAspect.beforeAll {
      for {
        config <- ZIO.service[DatabaseConfig]
        _ <- FlywayMigration.run(config)
      } yield ()
    }

}

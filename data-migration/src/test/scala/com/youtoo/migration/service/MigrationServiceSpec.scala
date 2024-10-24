package com.youtoo
package migration
package service

import cats.implicits.*

import zio.test.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*
import zio.jdbc.*

import com.youtoo.cqrs.service.*
import com.youtoo.cqrs.service.postgres.*

import com.youtoo.migration.repository.*

import com.youtoo.migration.model.*

object MigrationServiceSpec extends ZIOSpecDefault {
  def spec = suite("MigrationServiceSpec")(
    test("load returns expected migration using MigrationRepository") {
      check(migrationGen) { case expectedMigration =>
        val migrationId = expectedMigration.id

        val mockEnv = MigrationRepositoryMock.Load(
          equalTo(migrationId),
          value(expectedMigration.some),
        )

        (for {
          effect <- atomically(MigrationService.load(migrationId))
          testResult = assert(effect)(equalTo(expectedMigration.some))
        } yield testResult).provideSomeLayer[ZConnectionPool](mockEnv.toLayer >>> MigrationService.live())

      }
    } @@ TestAspect.samples(1),
    test("save returns expected result using MigrationRepository") {
      check(migrationGen) { case migration =>
        val expected = 1L

        val mockEnv = MigrationRepositoryMock.Save(
          equalTo(migration),
          value(expected),
        )

        (for {
          effect <- atomically(MigrationService.save(migration))
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool](mockEnv.toLayer >>> MigrationService.live())

      }
    } @@ TestAspect.samples(1),
    test("load many returns expected result using MigrationRepository") {
      check(Gen.some(keyGen), Gen.long, keyGen) { case (key, limit, id) =>
        val expected = Chunk(id)

        val mockEnv = MigrationRepositoryMock.LoadMany(
          equalTo((key, limit)),
          value(expected),
        )

        (for {
          effect <- atomically(MigrationService.loadMany(offset = key, limit = limit))
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool](mockEnv.toLayer >>> MigrationService.live())

      }
    } @@ TestAspect.samples(1),
  ).provideSomeLayerShared(ZConnectionMock.pool())

}

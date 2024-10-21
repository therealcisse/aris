package com.youtoo.cqrs
package migration
package service

import cats.implicits.*

import zio.test.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*
import zio.jdbc.*

import com.youtoo.cqrs.migration.model.*

import com.youtoo.cqrs.service.postgres.*

object MigrationProviderSpec extends ZIOSpecDefault {

  def spec = suite("MigrationProviderSpec")(
    test("should load migration using MigrationService") {
      check(migrationGen) { case migration =>
        val expected = migration.some
        val mockEnv = MigrationServiceMock.Load(
          equalTo(migration.id),
          value(expected),
        )

        (for {

          _ <- MigrationProvider.load(migration.id.asKey)

        } yield assertCompletes).provide((mockEnv.toLayer ++ ZConnectionMock.pool()) >>> MigrationProvider.live())
      }

    } @@ TestAspect.samples(1),
  )
}

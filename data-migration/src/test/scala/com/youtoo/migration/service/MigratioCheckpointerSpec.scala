package com.youtoo
package migration
package service

import zio.test.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*
import zio.jdbc.*

import com.youtoo.migration.model.*

import com.youtoo.cqrs.service.postgres.*

object MigrationCheckpointerSpec extends ZIOSpecDefault {

  def spec = suite("MigrationCheckpointerSpec")(
    test("should save migration using MigrationService") {
      check(migrationGen) { case migration =>
        val expected = 1L
        val mockEnv = MigrationServiceMock.Save(
          equalTo(migration),
          value(expected),
        )

        (for {

          _ <- MigrationCheckpointer.save(migration)

        } yield assertCompletes).provide((mockEnv.toLayer ++ ZConnectionMock.pool()) >>> MigrationCheckpointer.live())
      }

    } @@ TestAspect.samples(1),
  )
}

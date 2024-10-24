package com.youtoo
package ingestion
package service

import zio.test.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*
import zio.jdbc.*

import com.youtoo.ingestion.model.*

import com.youtoo.cqrs.service.postgres.*

object IngestionCheckpointerSpec extends ZIOSpecDefault {

  def spec = suite("IngestionCheckpointerSpec")(
    test("should save ingestion using IngestionService") {
      check(ingestionGen) { case ingestion =>
        val expected = 1L
        val mockEnv = IngestionServiceMock.Save(
          equalTo(ingestion),
          value(expected),
        )

        (for {

          _ <- IngestionCheckpointer.save(ingestion)

        } yield assertCompletes).provide((mockEnv.toLayer ++ ZConnectionMock.pool()) >>> IngestionCheckpointer.live())
      }

    } @@ TestAspect.samples(1),
  )
}

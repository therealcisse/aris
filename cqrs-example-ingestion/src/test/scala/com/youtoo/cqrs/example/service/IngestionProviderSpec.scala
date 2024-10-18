package com.youtoo.cqrs
package example
package service

import cats.implicits.*

import zio.test.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*
import zio.jdbc.*

import com.youtoo.cqrs.example.model.*

import com.youtoo.cqrs.service.postgres.*

object IngestionProviderSpec extends ZIOSpecDefault {

  def spec = suite("IngestionProviderSpec")(
    test("should load ingestion using IngestionService") {
      check(ingestionGen) { case ingestion =>
        val expected = ingestion.some
        val mockEnv = IngestionServiceMock.Load(
          equalTo(ingestion.id),
          value(expected),
        )

        (for {

          _ <- IngestionProvider.load(ingestion.id.asKey)

        } yield assertCompletes).provide((mockEnv.toLayer ++ ZConnectionMock.pool()) >>> IngestionProvider.live())
      }

    } @@ TestAspect.samples(1),
  )
}

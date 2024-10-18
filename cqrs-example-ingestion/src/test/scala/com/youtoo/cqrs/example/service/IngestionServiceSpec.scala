package com.youtoo.cqrs
package example
package service

import cats.implicits.*

import zio.test.*
import zio.test.Assertion.*
import zio.mock.Expectation.*
import zio.*
import zio.jdbc.*

import com.youtoo.cqrs.service.*
import com.youtoo.cqrs.service.postgres.*

import com.youtoo.cqrs.example.repository.*

import com.youtoo.cqrs.example.model.*

object IngestionServiceSpec extends ZIOSpecDefault {
  def spec = suite("IngestionServiceSpec")(
    test("load returns expected ingestion using IngestionRepository") {
      check(ingestionGen) { case expectedIngestion =>
        val ingestionId = expectedIngestion.id

        val mockEnv = IngestionRepositoryMock.Load(
          equalTo(ingestionId),
          value(expectedIngestion.some),
        )

        (for {
          effect <- atomically(IngestionService.load(ingestionId))
          testResult = assert(effect)(equalTo(expectedIngestion.some))
        } yield testResult).provideSomeLayer[ZConnectionPool](mockEnv.toLayer >>> IngestionService.live())

      }
    } @@ TestAspect.samples(1),
    test("save returns expected result using IngestionRepository") {
      check(ingestionGen) { case ingestion =>
        val expected = 1L

        val mockEnv = IngestionRepositoryMock.Save(
          equalTo(ingestion),
          value(expected),
        )

        (for {
          effect <- atomically(IngestionService.save(ingestion))
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool](mockEnv.toLayer >>> IngestionService.live())

      }
    } @@ TestAspect.samples(1),
    test("load many returns expected result using IngestionRepository") {
      check(Gen.some(keyGen), Gen.long, keyGen) { case (key, limit, id) =>
        val expected = Chunk(id)

        val mockEnv = IngestionRepositoryMock.LoadMany(
          equalTo((key, limit)),
          value(expected),
        )

        (for {
          effect <- atomically(IngestionService.loadMany(offset = key, limit = limit))
          testResult = assert(effect)(equalTo(expected))
        } yield testResult).provideSomeLayer[ZConnectionPool](mockEnv.toLayer >>> IngestionService.live())

      }
    } @@ TestAspect.samples(1),
  ).provideSomeLayerShared(ZConnectionMock.pool())

}

package com.youtoo.cqrs
package service

import com.youtoo.cqrs.service.postgres.*
import com.youtoo.cqrs.config.*

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.Codecs.*

import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.jdbc.*
import zio.schema.*
import java.time.Instant
import com.dimafeng.testcontainers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

object PostgresCQRSPersistenceSpec extends ZIOSpecDefault {
  case class DummyEvent(id: String, timestamp: Instant)

  object DummyEvent {
    given Schema[DummyEvent] = DeriveSchema.gen[DummyEvent]
  }

  def config(): ZLayer[Scope & PostgreSQLContainer, Throwable, DatabaseConfig] =
    ZLayer.fromFunction { (container: PostgreSQLContainer) =>
      DatabaseConfig(
        driverClassName = "org.postgresql.Driver",
        jdbcUrl = container.jdbcUrl,
        username = container.username,
        password = container.password,
        migrations = "migrations",
      )

    }

  def postgres(): ZLayer[Scope & PostgreSQLContainer, Throwable, ZConnectionPool] =
    val poolConfig: ULayer[ZConnectionPoolConfig] =
      ZLayer.succeed(ZConnectionPoolConfig.default)

    ZLayer.fromFunction { (container: PostgreSQLContainer) =>
      val props = Map(
        "user" -> container.username,
        "password" -> container.password,
      )

      poolConfig >>> ZConnectionPool.postgres(container.host, container.mappedPort(5432), container.databaseName, props)
    }.flatten

  def container(): ZLayer[Scope, Throwable, PostgreSQLContainer] =
    ZLayer {
      val a = ZIO.attemptBlocking {
        val container = PostgreSQLContainer(dockerImageNameOverride = DockerImageName.parse("postgres:15"))

        container.start()
        container
      }

      ZIO.acquireRelease(a)(container => ZIO.attemptBlocking(container.stop()).orDie)

    }

  def spec = suite("PostgresCQRSPersistenceSpec")(
    test("should save and retrieve events correctly") {
      for {
        config <- ZIO.service[DatabaseConfig]
        _ <- Migration.run(config)

        persistence <- ZIO.service[CQRSPersistence]

        version <- Version.gen
        event = Change(version = version, DummyEvent("test", Instant.now()))
        key <- Key.gen
        discriminator = Discriminator("DummyEvent")

        saveResult <- persistence.atomically(persistence.saveEvent(key, discriminator, event))

        a <- assert(saveResult)(equalTo(1L))

        events <- persistence.atomically(persistence.readEvents[DummyEvent](key, discriminator))
        b <- assert(events)(isNonEmpty)

      } yield a && b
    },
    test("should handle snapshot storage correctly") {
      for {
        config <- ZIO.service[DatabaseConfig]
        _ <- Migration.run(config)

        persistence <- ZIO.service[CQRSPersistence]
        key <- Key.gen
        version <- Version.gen

        saveSnapshotResult <- persistence.atomically(persistence.saveSnapshot(key, version))
        a <- assert(saveSnapshotResult)(equalTo(1L))

        snapshot <- persistence.atomically(persistence.readSnapshot(key))
        b <- assert(snapshot)(isSome(equalTo(version)))

      } yield a && b
    },
  ).provideSomeLayerShared(
    ZLayer
      .makeSome[TestEnvironment & Scope & Environment, ZConnectionPool & Migration & DatabaseConfig & CQRSPersistence](
        postgres(),
        config(),
        container(),
        PostgresCQRSPersistence.live(),
        Migration.live(),
      ),
  )
}

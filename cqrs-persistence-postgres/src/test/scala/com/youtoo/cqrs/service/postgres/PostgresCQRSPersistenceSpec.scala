package com.youtoo.cqrs
package service

import com.youtoo.cqrs.service.postgres.*
import com.youtoo.cqrs.config.*

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.Codecs.*

import cats.implicits.*

import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.jdbc.*
import zio.schema.*
import com.dimafeng.testcontainers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

object PostgresCQRSPersistenceSpec extends ZIOSpecDefault {
  case class DummyEvent(id: String)

  object DummyEvent {
    given Schema[DummyEvent] = DeriveSchema.gen[DummyEvent]

    val discriminator = Discriminator("DummyEvent")
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
        event = Change(version = version, DummyEvent("test"))
        key <- Key.gen

        saveResult <- persistence.atomically(persistence.saveEvent(key, DummyEvent.discriminator, event))

        a <- assert(saveResult)(equalTo(1L))

        events <- persistence.atomically(persistence.readEvents[DummyEvent](key, DummyEvent.discriminator))
        b <- assert(events)(isNonEmpty)

      } yield a && b
    },
    test("should retrieve events in sorted order") {
      for {
        config <- ZIO.service[DatabaseConfig]
        _ <- Migration.run(config)

        persistence <- ZIO.service[CQRSPersistence]

        events <- ZIO.collectAll {
          (0 to 100).map { i =>
            TestClock.adjust(Duration.fromMillis(1)) *> (for {
              version <- Version.gen
              ch = Change(version = version, payload = DummyEvent(s"$i"))

            } yield ch)
          }

        }

        key <- Key.gen

        _ <- persistence.atomically {
          ZIO.foreachDiscard(events) { e =>
            persistence.saveEvent(key, DummyEvent.discriminator, e)
          }
        }

        es <- persistence.atomically(persistence.readEvents[DummyEvent](key, DummyEvent.discriminator))

        a <- assert(es)(equalTo(es.sortBy(_.version)))

      } yield a
    },
    test("should retrieve events from given version") {
      for {
        config <- ZIO.service[DatabaseConfig]
        _ <- Migration.run(config)

        persistence <- ZIO.service[CQRSPersistence]

        events <- ZIO.collectAll {
          (0 to 100).map { i =>
            TestClock.adjust(Duration.fromMillis(1L)) *> (for {
              version <- Version.gen
              ch = Change(version = version, payload = DummyEvent(s"$i"))

            } yield ch)
          }

        }

        key <- Key.gen

        _ <- persistence.atomically {
          ZIO.foreachDiscard(events) { e =>
            persistence.saveEvent(key, DummyEvent.discriminator, e)
          }
        }

        es <- persistence.atomically(persistence.readEvents[DummyEvent](key, DummyEvent.discriminator))

        a <- assert(es)(equalTo(events))

        max = es.maxBy(_.version).version

        es1 <- persistence.atomically(
          persistence.readEvents[DummyEvent](key, DummyEvent.discriminator, snapshotVersion = max),
        )

        b <- assert(es1)(isEmpty)

        events1 <- ZIO.collectAll {
          (0 to 100).map { i =>
            TestClock.adjust(Duration.fromMillis(1L)) *> (for {
              version <- Version.gen
              ch = Change(version = version, payload = DummyEvent(s"$i"))

            } yield ch)
          }

        }

        _ <- persistence.atomically {
          ZIO.foreachDiscard(events1) { e =>
            persistence.saveEvent(key, DummyEvent.discriminator, e)
          }
        }

        es2 <- persistence.atomically(
          persistence.readEvents[DummyEvent](key, DummyEvent.discriminator, snapshotVersion = max),
        )

        c <- assert(es2)(equalTo(events1))

        max1 = es2.maxBy(_.version).version
        es3 <- persistence.atomically(
          persistence.readEvents[DummyEvent](key, DummyEvent.discriminator, snapshotVersion = max1),
        )
        d <- assert(es3)(isEmpty)
      } yield a && b && c && d
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

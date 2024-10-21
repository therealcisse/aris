package com.youtoo.cqrs
package service
package postgres

import com.youtoo.cqrs.config.*

import zio.*
import zio.test.*
import zio.jdbc.*
import com.dimafeng.testcontainers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

abstract class PgSpec extends ZIOSpec[ZConnectionPool & DatabaseConfig & FlywayMigration] {
  def config(): ZLayer[PostgreSQLContainer, Throwable, DatabaseConfig] =
    ZLayer.fromFunction { (container: PostgreSQLContainer) =>
      DatabaseConfig(
        driverClassName = "org.postgresql.Driver",
        jdbcUrl = container.jdbcUrl,
        username = container.username,
        password = container.password,
        migrations = "migrations",
      )

    }

  def postgres(): ZLayer[PostgreSQLContainer, Throwable, ZConnectionPool] =
    val poolConfig: ULayer[ZConnectionPoolConfig] =
      ZLayer.succeed(ZConnectionPoolConfig.default)

    ZLayer.fromFunction { (container: PostgreSQLContainer) =>
      val props = Map(
        "user" -> container.username,
        "password" -> container.password,
      )

      poolConfig >>> ZConnectionPool.postgres(container.host, container.mappedPort(5432), container.databaseName, props)
    }.flatten

  def container(): ZLayer[Any, Throwable, PostgreSQLContainer] =
    ZLayer.scoped {
      val a = ZIO.attemptBlocking {
        val container = PostgreSQLContainer(dockerImageNameOverride = DockerImageName.parse("postgres:15"))

        container.start()
        container
      }

      ZIO.acquireRelease(a)(container => ZIO.attemptBlocking(container.stop()).orDie)

    }

  def bootstrap: ZLayer[Any, Throwable, ZConnectionPool & DatabaseConfig & FlywayMigration] =
    container() >>> ZLayer.makeSome[PostgreSQLContainer, ZConnectionPool & DatabaseConfig & FlywayMigration](
      postgres(),
      config(),
      FlywayMigration.live(),
    )

}

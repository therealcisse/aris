package com.github
package aris
package postgres

import com.github.aris.postgres.config.*
import com.github.aris.service.*
import com.github.aris.service.postgres.*

import zio.*
import zio.test.*
import com.dimafeng.testcontainers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

abstract class PgSpec extends ZIOSpec[DatabaseConfig & FlywayMigration] {
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

  def postgres(): ZLayer[PostgreSQLContainer, Throwable, TransactionManager.Aux[ZConnection]] =
    val poolConfig: ULayer[ZConnectionPoolConfig] =
      ZLayer.succeed(ZConnectionPoolConfig.default)

    ZLayer.fromFunction { (container: PostgreSQLContainer) =>
      val props = Map(
        "user" -> container.username,
        "password" -> container.password,
      )

      poolConfig >>> ZConnectionPool.postgres(
        container.host,
        container.mappedPort(5432),
        container.databaseName,
        props,
      ) >>> ZIOJdbcTransactionManager.live()
    }.flatten

  def container(): ZLayer[Any, Throwable, PostgreSQLContainer] =
    ZLayer.scoped {
      val a = ZIO.attemptBlocking {
        val container = PostgreSQLContainer(dockerImageNameOverride = DockerImageName.parse("postgres:16"))

        container.start()
        container
      }

      ZIO.acquireRelease(a)(container => ZIO.attemptBlocking(container.stop()).ignoreLogged)

    }

  def bootstrap: ZLayer[Any, Throwable, DatabaseConfig & FlywayMigration] =
    container() >>> ZLayer.makeSome[PostgreSQLContainer, DatabaseConfig & FlywayMigration](
      postgres(),
      config(),
      FlywayMigration.live(),
    )

}

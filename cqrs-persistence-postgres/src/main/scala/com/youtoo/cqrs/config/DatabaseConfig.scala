package com.youtoo.cqrs
package config

import zio.*

import cats.implicits.*

case class DatabaseConfig(
  driverClassName: String,
  jdbcUrl: String,
  username: String,
  password: String,
  migrations: String,
)

object DatabaseConfig {
  import zio.jdbc.*

  val connectionPool: ZLayer[ZConnectionPoolConfig, Throwable, ZConnectionPool] =
    ZLayer.fromZIO {
      for {
        config <- ZIO.config[DatabaseConfig]

        props = Map(
          "user" -> config.username,
          "password" -> config.password,
        )

      } yield ZConnectionPool.postgres("localhost", 5432, "cqrs", props)

    }.flatten

  val createZIOPoolConfig: ULayer[ZConnectionPoolConfig] =
    ZLayer.succeed(ZConnectionPoolConfig.default)

  val pool: ZLayer[Any, Throwable, ZConnectionPool] = createZIOPoolConfig >>> connectionPool

  given Config[DatabaseConfig] =
    (
      Config.string("driver").optional ++
        Config.string("url").optional ++ Config.string("username").optional ++ Config
          .string("password")
          .optional ++ Config.string("migrations").optional
    ).nested("database").map { case (driverClassName, jdbcUrl, username, password, migrations) =>
      (
        driverClassName orElse "org.postgresql.Driver".some,
        jdbcUrl,
        username,
        password,
        migrations orElse "migrations".some,
      ) mapN {
        case (
              driverClassName,
              jdbcUrl,
              username,
              password,
              migrations,
            ) =>
          DatabaseConfig(driverClassName, jdbcUrl, username, password, migrations)

      } getOrElse DatabaseConfig.postgres

    }

  val postgres = DatabaseConfig(
    "org.postgresql.Driver",
    "jdbc:postgresql://localhost:5432/cqrs",
    "cqrs",
    "admin",
    "migrations",
  )

}

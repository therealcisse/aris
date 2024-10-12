package com.youtoo.cqrs
package example
package config

import zio.*

import cats.implicits.*

case class DatabaseConfig(
  driverName: String,
  databaseUrl: String,
  username: String,
  password: String,
  migrations: String,
)

object DatabaseConfig {
  import zio.jdbc.*

  val connectionPool: ZLayer[ZConnectionPoolConfig, Throwable, ZConnectionPool] =
    ZLayer {
      for {
        config <- ZIO.config[DatabaseConfig]

        props = Map(
          "user"     -> config.username,
          "password" -> config.password,
        )

      } yield ZConnectionPool.postgres("localhost", 5432, "cqrs", props)

    }.flatten

  val createZIOPoolConfig: ULayer[ZConnectionPoolConfig] =
    ZLayer.succeed(ZConnectionPoolConfig.default)

  val pool = createZIOPoolConfig >>> connectionPool

  given Config[DatabaseConfig] =
    (
      Config.string("driver").optional ++
        Config.string("url").optional ++ Config.string("username").optional ++ Config
          .string("password")
          .optional ++ Config.string("migrations").optional
    ).map { case (driverName, databaseUrl, username, password, migrations) =>
      (
        driverName,
        databaseUrl,
        username,
        password,
        migrations orElse "migrations".some,
      ) mapN {
        case (
              driverName,
              databaseUrl,
              username,
              password,
              migrations,
            ) =>
          DatabaseConfig(driverName, databaseUrl, username, password, migrations)

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

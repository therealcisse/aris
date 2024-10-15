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

  import scala.util.matching.Regex

  object JDBCUrlExtractor {
    val pattern: Regex = "jdbc:\\w+://([\\w.-]+):(\\d+)/\\w+".r

    inline def unapply(string: String): Option[(String, Int)] = string match {
      case pattern(host, port) => Some((host, port.toInt))
      case _ => None
    }
  }

  val connectionPool: ZLayer[ZConnectionPoolConfig, Throwable, ZConnectionPool] =
    ZLayer.fromZIO {
      for {
        config <- ZIO.config[DatabaseConfig]

        props = Map(
          "user" -> config.username,
          "password" -> config.password,
        )

        pool = config.jdbcUrl match {
          case JDBCUrlExtractor(host, port) => ZConnectionPool.postgres(host, port, "cqrs", props)
          case _ => ZConnectionPool.postgres("localhost", 5432, "cqrs", props)
        }

      } yield pool

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

      } getOrElse (throw IllegalArgumentException("Load database config"))

    }

}

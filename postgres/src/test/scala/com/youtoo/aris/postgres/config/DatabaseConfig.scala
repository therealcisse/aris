package com.youtoo
package aris
package postgres
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
    val pattern: Regex = "jdbc:\\w+://([\\w.-]+):(\\d+)/(\\w+)".r

    inline def unapply(string: String): Option[(String, Int, String)] = string match {
      case pattern(host, port, name) => Some((host, port.toInt, name))
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
          "prepareThreshold" -> "3",
          "binaryTransfer" -> "true",
          "loggerLevel" -> "OFF",
          "tcpKeepAlive" -> "true",
        )

        pool = config.jdbcUrl match {
          case JDBCUrlExtractor(host, port, name) => ZConnectionPool.postgres(host, port, name, props)
          case _ => ZConnectionPool.postgres("localhost", 5432, "youtoo", props)
        }

      } yield pool

    }.flatten

  val createZIOPoolConfig: ULayer[ZConnectionPoolConfig] =
    ZLayer.succeed(ZConnectionPoolConfig.default)

  val pool: ZLayer[Any, Throwable, ZConnectionPool] = createZIOPoolConfig >>> connectionPool

  given Config[DatabaseConfig] = (
    Config.string("driver").optional ++
      Config.string("url").optional ++ Config.string("username").optional ++ Config
        .string("password")
        .optional ++ Config.string("migrations").optional
  ).nested("database") map { case (driverClassName, jdbcUrl, username, password, migrations) =>
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

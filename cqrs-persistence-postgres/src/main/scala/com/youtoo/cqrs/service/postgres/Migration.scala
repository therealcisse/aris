package com.youtoo.cqrs
package service
package postgres

import com.youtoo.cqrs.config.*

import zio.*

trait Migration {
  def run(config: DatabaseConfig): Task[Unit]

}

object Migration {
  inline def run(config: DatabaseConfig): RIO[Migration, Unit] =
    ZIO.serviceWithZIO(_.run(config))

  def live(): ZLayer[Any, Throwable, Migration] =
    ZLayer.succeed {
      new Migration {
        def run(config: DatabaseConfig): Task[Unit] =
          runMigration(config)

      }
    }

  def runMigration(config: DatabaseConfig): Task[Unit] =
    ZIO.attemptBlocking {
      import org.flywaydb.core.Flyway
      import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

      val hikariConfig = HikariConfig()

      hikariConfig.setDriverClassName(config.driverClassName)
      hikariConfig.setJdbcUrl(config.jdbcUrl)
      hikariConfig.setUsername(config.username)
      hikariConfig.setPassword(config.password)

      val dataSource = HikariDataSource(hikariConfig)

      val flyway = Flyway
        .configure()
        .dataSource(dataSource)
        .locations(config.migrations)
        .outOfOrder(true)
        .load()

      flyway.migrate()
    }

}

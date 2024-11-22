package com.youtoo
package cqrs
package service
package postgres

import com.youtoo.cqrs.config.*

import zio.telemetry.opentelemetry.tracing.Tracing

import zio.*

trait FlywayMigration {
  def run(config: DatabaseConfig): RIO[Tracing, Unit]

}

object FlywayMigration {
  inline def run(config: DatabaseConfig): RIO[FlywayMigration & Tracing, Unit] =
    ZIO.serviceWithZIO[FlywayMigration](_.run(config))

  def live(): ZLayer[Any, Throwable, FlywayMigration] =
    ZLayer.succeed {
      new FlywayMigration {
        def run(config: DatabaseConfig): RIO[Tracing, Unit] =
          runMigration(config).tapError { e =>
            Log.error("Migration failed", e)
          }

      }
    }

  def runMigration(config: DatabaseConfig): RIO[Tracing, Unit] =
    ZIO.scoped {
      import org.flywaydb.core.Flyway
      import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

      val hikariConfig = HikariConfig()

      hikariConfig.setDriverClassName(config.driverClassName)
      hikariConfig.setJdbcUrl(config.jdbcUrl)
      hikariConfig.setUsername(config.username)
      hikariConfig.setPassword(config.password)

      ZIO.acquireRelease(ZIO.attemptBlocking(HikariDataSource(hikariConfig)))(ds =>
        ZIO.attemptBlocking(ds.close()).ignoreLogged,
      ) flatMap { dataSource =>
        ZIO.attemptBlocking {

          val flyway = Flyway
            .configure()
            .dataSource(dataSource)
            .locations(config.migrations)
            .load()

          flyway.migrate()
        }

      }

    }

}

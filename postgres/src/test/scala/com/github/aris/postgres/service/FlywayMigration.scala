package com.github
package aris
package postgres

import com.github.aris.postgres.config.*

import zio.*

trait FlywayMigration {
  def run(config: DatabaseConfig): Task[Unit]

}

object FlywayMigration {
  inline def run(config: DatabaseConfig): RIO[FlywayMigration, Unit] =
    ZIO.serviceWithZIO(_.run(config))

  def live(): ZLayer[Any, Throwable, FlywayMigration] =
    ZLayer.succeed {
      new FlywayMigration {
        def run(config: DatabaseConfig): Task[Unit] =
          runMigration(config).tapError { e =>
            ZIO.logErrorCause("Migration failed", Cause.die(e))
          }

      }
    }

  def runMigration(config: DatabaseConfig): Task[Unit] =
    ZIO.scoped {
      import org.flywaydb.core.Flyway
      import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

      val hikariConfig = HikariConfig()

      hikariConfig.setDriverClassName(config.driverClassName)
      hikariConfig.setJdbcUrl(config.jdbcUrl)
      hikariConfig.setUsername(config.username)
      hikariConfig.setPassword(config.password)

      val props = new java.util.Properties()
      props.setProperty("tcpKeepAlive", "true")

      hikariConfig.setDataSourceProperties(props)

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

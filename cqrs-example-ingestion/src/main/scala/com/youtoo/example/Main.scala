package com.youtoo.cqrs
package example

import zio.jdbc.*

import com.youtoo.cqrs.config.*
import com.youtoo.cqrs.example.model.*

import com.youtoo.cqrs.Codecs.*

import com.youtoo.cqrs.domain.*

import com.youtoo.cqrs.service.*
import com.youtoo.cqrs.service.postgres.*

import zio.*
import zio.prelude.*

object Main extends ZIOApp {
  type Environment = Migration & ZConnectionPool & CQRSPersistence

  given environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  val bootstrap: ZLayer[Any, Nothing, Environment] =
    ZLayer
      .make[Environment](
        DatabaseConfig.pool,
        PostgresCQRSPersistence.live(),
        Migration.live(),
      )
      .orDie ++ Runtime.setConfigProvider(ConfigProvider.envProvider)

  val run: ZIO[Environment & Scope, Throwable, Unit] =
    for {
      config <- ZIO.config[DatabaseConfig]

      _ <- Migration.run(config)

      id <- Ingestion.Id.gen
      version <- Version.gen
      // agg = Aggregate(id = id.value, version = version)
      //
      service <- ZIO.service[CQRSPersistence]
      //
      // _ <- CQRSPersistence.atomically {
      //   for {
      //     _ <- service.saveAggregate(agg)
      //
      //     a <- service.readAggregate(agg.id)
      //
      //     _ = println(a)
      //
      //   } yield ()

      id <- Ingestion.Id.gen
      timestamp <- Timestamp.now
      version0 <- Version.gen
      e0 = IngestionEvent.IngestionStarted(id, timestamp)
      version1 <- Version.gen
      e1 = IngestionEvent.IngestionFilesResolved(files = Set("1"))

      _ <- service.atomically(
        service.saveEvent(id.asKey, IngestionEvent.discriminator, Change(version = version0, payload = e0)),
      )
      _ <- service.atomically(
        service.saveEvent(id.asKey, IngestionEvent.discriminator, Change(version = version1, payload = e1)),
      )

      events <- service.atomically(service.readEvents[IngestionEvent](id.asKey, IngestionEvent.discriminator))

      _ = println(events.mkString("[\n", ",\n ", "\n]"))

      _ = println(e0)
      _ = println(e1)
      _ = println(
        summon[EventHandler[IngestionEvent, Ingestion]].applyEvents(
          NonEmptyList(
            Change(version = version0, payload = e0),
            Change(version = version1, payload = e1),
          ),
        ),
      )

      // }

    } yield ()
}

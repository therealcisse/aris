package com.youtoo.cqrs
package example

import com.youtoo.cqrs.example.config.*
import com.youtoo.cqrs.example.model.*

import com.youtoo.cqrs.domain.*

import com.youtoo.cqrs.service.*
import com.youtoo.cqrs.service.postgres.*

import zio.*

object Main extends ZIOApp {
  type Environment = Migration & CQRSPersistence

  given environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  val bootstrap: ZLayer[Any, Nothing, Environment] =
    ZLayer
      .make[Environment](
        DatabaseConfig.pool >>> PostgresCQRSPersistence.live(),
        Migration.live(),
      )
      .orDie ++ Runtime.setConfigProvider(ConfigProvider.envProvider)

  val run: ZIO[Environment & Scope, Throwable, Unit] =
    for {
      _ <- Migration.run

      id <- Ingestion.Id.gen
      agg = Aggregate(id = Key(id.value), version = Version(0))

      service <- ZIO.service[CQRSPersistence]

      _ <- service.saveAggregate(agg)

      a <- service.readAggregate(agg.id)

      _ = println(a)

    } yield ()
}

package com.youtoo
package service

import com.youtoo.ingestion.service.*
import com.youtoo.ingestion.model.*

import zio.mock.*

import zio.*

object IngestionProviderMock extends Mock[IngestionProvider] {

  object Load extends Effect[Key, Throwable, Option[Ingestion]]

  val compose: URLayer[Proxy, IngestionProvider] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new IngestionProvider {
        def load(id: Key): Task[Option[Ingestion]] = proxy(Load, id)
      }
    }

}

package com.youtoo
package ingestion
package service

import com.youtoo.ingestion.model.*

import zio.mock.*

import zio.*

object IngestionCheckpointerMock extends Mock[IngestionCheckpointer] {

  object Save extends Effect[Ingestion, Throwable, Unit]

  val compose: URLayer[Proxy, IngestionCheckpointer] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new IngestionCheckpointer {
        def save(o: Ingestion): Task[Unit] = proxy(Save, o)
      }
    }

}

package com.youtoo.cqrs
package service

import com.youtoo.cqrs.service.*
import com.youtoo.cqrs.example.service.*
import com.youtoo.cqrs.example.model.*

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

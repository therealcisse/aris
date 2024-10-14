package com.youtoo.cqrs
package service

import com.youtoo.cqrs.domain.*

import zio.mock.*

import zio.*
import zio.jdbc.*
import zio.schema.codec.*

class MockProvider extends Mock[Provider[?]] {
  object Load extends Effect[Key, Throwable, Option[?]]

  val compose: URLayer[Proxy, Provider[?]] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new Provider[?] {
        def load(id: Key): Task[Option[?]] =
          proxy(Load, id)
      }
    }
}

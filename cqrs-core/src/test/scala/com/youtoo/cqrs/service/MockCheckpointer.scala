package com.youtoo.cqrs
package service

import zio.mock.*

import com.youtoo.cqrs.domain.*

import zio.*
import zio.jdbc.*
import zio.schema.codec.*

trait MockCheckpointer[T: izumi.reflect.Tag] extends Mock[Checkpointer[T]] {
  object Save extends Effect[T, Throwable, Unit]

  val compose: URLayer[Proxy, Checkpointer[T]] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new Checkpointer[T] {
        def save(o: T): Task[Unit] =
          proxy(Save, o)

      }
    }
}

package com.youtoo.cqrs
package example
package model

import io.github.thibaultmeyer.cuid.CUID

import zio.*
import zio.json.*

import zio.prelude.*

case class Ingestion(id: Ingestion.Id)

object Ingestion {
  type Id = Id.Type

  object Id extends Newtype[CUID] {
    extension (a: Id) inline def value: CUID = Id.unwrap(a)

    def gen: Task[Id] = ZIO.attempt(Id(CUID.randomCUID2(32)))

    inline def fromString(s: String): Task[Id] = ZIO.attempt(Id(CUID.fromString(s)))

    given JsonDecoder[Id] = JsonDecoder.string.map(s => Id(CUID.fromString(s)))
    given JsonEncoder[Id] = JsonEncoder.string.contramap(_.toString)
  }

}

package com.youtoo.cqrs
package service
package postgres

import zio.schema.*
import zio.schema.codec.*

import zio.*
import zio.jdbc.*

trait JdbcCodecs {

  // given [T: Schema]: JdbcDecoder[T] = JdbcDecoder.fromSchema[T]

  given SqlFragment.Setter[Key] = SqlFragment.Setter[String].contramap(_.value.toString)
  given SqlFragment.Setter[Version] = SqlFragment.Setter[String].contramap(_.value)
  given SqlFragment.Setter[Timestamp] = SqlFragment.Setter[Long].contramap(_.value)
  given SqlFragment.Setter[Discriminator] = SqlFragment.Setter[String].contramap(_.value)

  inline def byteArrayDecoder[T: BinaryCodec]: JdbcDecoder[T] =
    JdbcDecoder[Array[Byte]].map(array =>
      summon[BinaryCodec[T]]
        .decode(Chunk(array*))
        .getOrElse(throw IllegalArgumentException(s"""Can't decode array: ${new String(array)}""")),
    )

  given [T: BinaryCodec]: JdbcEncoder[T] =
    JdbcEncoder[Chunk[Byte]].contramap(t => summon[BinaryCodec[T]].encode(t))

}

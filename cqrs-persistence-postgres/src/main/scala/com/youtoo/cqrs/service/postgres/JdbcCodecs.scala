package com.youtoo
package cqrs
package service
package postgres

import zio.schema.*
import zio.schema.codec.*

import zio.*
import zio.jdbc.*

trait JdbcCodecs {
  def toJson[A: Schema](value: A): Chunk[Byte] =
    val jsonCodec = JsonCodec.schemaBasedBinaryCodec(Schema[A])
    jsonCodec.encode(value)

  given SqlFragment.Setter[Key] = SqlFragment.Setter[Long].contramap(_.value)
  given SqlFragment.Setter[Version] = SqlFragment.Setter[Long].contramap(_.value)
  given SqlFragment.Setter[Timestamp] = SqlFragment.Setter[Long].contramap(_.value)
  given SqlFragment.Setter[Discriminator] = SqlFragment.Setter[String].contramap(_.value)
  given SqlFragment.Setter[Namespace] = SqlFragment.Setter[Int].contramap(_.value)

  inline def byteArrayDecoder[T: BinaryCodec]: JdbcDecoder[T] =
    JdbcDecoder[Array[Byte]].map { array =>
      summon[BinaryCodec[T]]
        .decode(Chunk(array*))
        .getOrElse(throw IllegalArgumentException("""Can't decode array"""))
    }

  given JdbcDecoder[Version] = JdbcDecoder[Long].map(Version.apply)
  given JdbcDecoder[Key] = JdbcDecoder[Long].map(Key.apply)
  given JdbcDecoder[Timestamp] = JdbcDecoder[Long].map(Timestamp.apply)

}

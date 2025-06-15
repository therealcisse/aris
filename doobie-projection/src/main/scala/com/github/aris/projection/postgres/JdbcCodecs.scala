package com.github
package aris
package projection
package postgres

import cats.implicits.*

import zio.schema.*
import zio.schema.codec.*

import zio.*
import zio.prelude.*

import doobie.*
import doobie.implicits.*

trait JdbcCodecs {
  def toJson[A: Schema](value: A): String =
    JsonCodec.schemaBasedBinaryCodec(Schema[A]).encode(value).asString

  given Meta[Version] = Meta[Long].timap(Version.wrap)(Version.unwrap)
  given Meta[Timestamp] = Meta[Long].timap(Timestamp.wrap)(Timestamp.unwrap)
  given Meta[Key] = Meta[Long].timap(Key.wrap)(Key.unwrap)
  given Meta[Namespace] = Meta[String].timap(Namespace.wrap)(Namespace.unwrap)
  given Meta[Discriminator] = Meta[String].timap(Discriminator.wrap)(Discriminator.unwrap)
  given Meta[EventTag] = Meta[String].timap(EventTag.wrap)(EventTag.unwrap)
  given Meta[Projection.Name] = Meta[String].timap(Projection.Name.wrap)(Projection.Name.unwrap)
  given Meta[Projection.VersionId] = Meta[String].timap(Projection.VersionId.wrap)(Projection.VersionId.unwrap)

  inline def byteArrayReader[Event: BinaryCodec]: Read[Event] =
    Read[Array[Byte]].map { case (bytes) =>
      summon[BinaryCodec[Event]].decode(Chunk(bytes*)) match {
        case Left(error) => throw new Exception(s"Failed to decode bytes into Event: $error")
        case Right(value) => value
      }
    }

  extension (t: TimeInterval)
    def toSql: Fragment =
      fr"timestamp >= ${t.start} AND timestamp < ${t.end}"

  extension (o: FetchOptions)
    def toSql: (Option[Fragment], Option[Fragment], Fragment) =
      val offsetQuery = o.offset.map(offset =>
        o.order match {
          case FetchOptions.Order.asc => fr"version > $offset"
          case FetchOptions.Order.desc => fr"version < $offset"
        },
      )
      val limitQuery = o.limit.map(limit => fr"LIMIT $limit")
      val orderQuery = o.order match {
        case FetchOptions.Order.asc => fr"ORDER BY version ASC"
        case FetchOptions.Order.desc => fr"ORDER BY version DESC"
      }
      (offsetQuery, limitQuery, orderQuery)

  extension (o: NonEmptyList[Namespace])
    @scala.annotation.targetName("toSql_NonEmptyList_Namespace")
    def toSql: Fragment = o match {
      case NonEmptyList.Single(n) => n.toSql
      case NonEmptyList.Cons(n, ns) =>
        fr0"namespace IN (" ++ (n :: ns).toList.map(n => fr0"$n").intercalate(fr",") ++ fr")"
    }

  extension (o: Namespace)
    @scala.annotation.targetName("toSql_Namespace")
    def toSql: Fragment = o match {
      case n => fr"namespace = $n"
    }

  extension (t: EventTag)
    @scala.annotation.targetName("toSql_EventTag")
    def toSql: Fragment = fr"t.tag = $t"

  extension (f: Fragment) inline def isEmpty: Boolean = f.internals.elements.isEmpty

}

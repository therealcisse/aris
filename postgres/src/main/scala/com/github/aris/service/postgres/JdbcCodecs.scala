package com.github
package aris
package service
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
  given Meta[Namespace] = Meta[Int].timap(Namespace.wrap)(Namespace.unwrap)
  given Meta[Discriminator] = Meta[String].timap(Discriminator.wrap)(Discriminator.unwrap)

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

  extension (q: PersistenceQuery)
    def toSql: Option[Fragment] =
      q match {
        case q: PersistenceQuery.Condition => q.toSql
        case PersistenceQuery.any(condition, more*) =>
          val qs = more.foldLeft(condition.toSql.toList) { case (qs, n) =>
            n.toSql match {
              case Some(q) => (q :: qs)
              case _ => qs
            }
          }

          if qs.isEmpty then None
          else
            (
              fr"(" ++ qs.intercalate(fr" OR ") ++ fr")"
            ).some

        case PersistenceQuery.forall(query, more*) =>
          val qs = more.foldLeft(query.toSql.toList) { case (qs, n) =>
            n.toSql match {
              case Some(q) => (q :: qs)
              case _ => qs
            }
          }

          if qs.isEmpty then None else (fr"(" ++ qs.intercalate(fr" AND ") ++ fr")").some
      }

  extension (q: PersistenceQuery.Condition)
    def toSql: Option[Fragment] =
      val nsQuery: Fragment = q.namespace.fold(Fragment.empty)(_.toSql)
      val qss = List(nsQuery).filterNot(_.isEmpty)

      if qss.isEmpty then None
      else (fr"(" ++ qss.intercalate(fr"AND") ++ fr")").some


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


  extension (f: Fragment) inline def isEmpty: Boolean = f.internals.elements.isEmpty

}

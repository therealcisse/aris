package com.youtoo
package cqrs
package service
package postgres

import cats.implicits.*

import zio.schema.*
import zio.schema.codec.*

import zio.*
import zio.jdbc.*
import zio.prelude.*

trait JdbcCodecs {
  def toJson[A: Schema](value: A): Chunk[Byte] =
    val jsonCodec = JsonCodec.schemaBasedBinaryCodec(Schema[A])
    jsonCodec.encode(value)

  given SqlFragment.Setter[Key] = SqlFragment.Setter[Long].contramap(_.value)
  given SqlFragment.Setter[Version] = SqlFragment.Setter[Long].contramap(_.value)
  given SqlFragment.Setter[Timestamp] = SqlFragment.Setter[Long].contramap(_.value)
  given SqlFragment.Setter[Discriminator] = SqlFragment.Setter[String].contramap(_.value)
  given SqlFragment.Setter[Namespace] = SqlFragment.Setter[Int].contramap(_.value)
  given SqlFragment.Setter[Reference] = SqlFragment.Setter[Key].contramap(_.value)

  inline def byteArrayDecoder[T: BinaryCodec]: JdbcDecoder[T] =
    JdbcDecoder[Array[Byte]].map { array =>
      summon[BinaryCodec[T]]
        .decode(Chunk(array*))
        .getOrElse(throw IllegalArgumentException("""Can't decode array"""))
    }

  given JdbcDecoder[Version] = JdbcDecoder[Long].map(Version.apply)
  given JdbcDecoder[Key] = JdbcDecoder[Long].map(Key.apply)
  given JdbcDecoder[Timestamp] = JdbcDecoder[Long].map(Timestamp.apply)

  extension (o: FetchOptions)
    def toSql: Option[SqlFragment] =
      val offsetQuery = o.offset.map(offset => sql" OFFSET $offset ")
      val limitQuery = o.limit.map(limit => sql" LIMIT $limit ")

      (offsetQuery, limitQuery) match {
        case (None, None) => None
        case (Some(l), None) => Some(l)
        case (None, Some(r)) => Some(r)
        case (Some(l), Some(r)) => Some(l ++ r)
      }

  extension (q: PersistenceQuery)
    def toSql: Option[SqlFragment] =
      q match {
        case q: PersistenceQuery.Condition => q.toSql
        case PersistenceQuery.any(condition, more*) =>
          val qs = more.foldLeft(condition.toSql.toList) { case (qs, n) =>
            n.toSql match {
              case Some(q) => (q :: qs)
              case _ => qs
            }
          }

          if qs.isEmpty then None else qs.mkFragment(sql"(", sql" OR ", sql")").some

        case PersistenceQuery.forall(query, more*) =>
          val qs = more.foldLeft(query.toSql.toList) { case (qs, n) =>
            n.toSql match {
              case Some(q) => (q :: qs)
              case _ => qs
            }
          }

          if qs.isEmpty then None else qs.mkFragment(sql"(", sql" AND ", sql")").some

      }

  extension (q: PersistenceQuery.Condition)
    def toSql: Option[SqlFragment] =
      val nsQuery: SqlFragment = q.namespace.fold(SqlFragment.empty)(_.toSql)
      val hierarchyQuery: SqlFragment = q.hierarchy.fold(SqlFragment.empty)(_.toSql)
      val referenceQuery: SqlFragment = q.reference.fold(SqlFragment.empty)(_.toSql)

      val propQueries: List[SqlFragment] =
        q.props
          .map(_.toList.map(_.toSql))
          .getOrElse(Nil)

      val qss = (nsQuery :: hierarchyQuery :: referenceQuery :: propQueries)
        .filterNot(_.isEmpty)

      if qss.isEmpty then None
      else qss.mkFragment(sql"(", sql" AND ", sql")").some

  extension (o: NonEmptyList[EventProperty])
    @scala.annotation.targetName("toSql_NonEmptyList_EventProperty")
    def toSql: SqlFragment = o match {
      case NonEmptyList.Single(p) => p.toSql
      case NonEmptyList.Cons(p, ps) => ps.foldLeft(p.toSql) { case (a, n) => a ++ sql" AND " ++ n.toSql }

    }

  extension (o: NonEmptyList[Namespace])
    @scala.annotation.targetName("toSql_NonEmptyList_Namespace")
    def toSql: SqlFragment = o match {
      case NonEmptyList.Single(n) => n.toSql
      case NonEmptyList.Cons(n, ns) => sql"namespace IN (${n :: ns.toList})"

    }

  extension (o: Hierarchy)
    def toSql: SqlFragment = o match {
      case Hierarchy.Child(parentId) => sql"""parent_id = $parentId"""
      case Hierarchy.GrandChild(grandParentId) => sql"""grand_parent_id = $grandParentId"""
      case Hierarchy.Descendant(grandParentId, parentId) =>
        sql"""parent_id = $parentId AND grand_parent_id = $grandParentId"""

    }

  extension (o: Namespace)
    @scala.annotation.targetName("toSql_Namespace")
    def toSql: SqlFragment = o match {
      case n => sql"""namespace = $n"""

    }
  extension (o: Reference)
    @scala.annotation.targetName("toSql_Reference")
    def toSql: SqlFragment = o match {
      case r => sql"""reference = $r"""

    }
  extension (o: EventProperty)
    def toSql: SqlFragment = o match {
      case EventProperty(key, value) =>
        s"""props->>'$key' = """ ++ sql"""$value"""
    }

  extension (q: SqlFragment) inline def isEmpty: Boolean = q.segments.isEmpty

}

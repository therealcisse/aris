package com.github
package aris
package service
package postgres

import cats.implicits.*

import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*

object PostgresPersistenceQuerySpec extends ZIOSpecDefault, JdbcCodecs {

  def spec = suite("PostgresPersistenceQuerySpec")(
    suite("PersistenceQuery.toSql")(
      test("Namespace.toSql should produce correct SQL fragment") {
        check(namespaceGen) { ns =>
          val sqlFragment = ns.toSql
          val expectedFragment = sql"namespace = ${ns.value}"

          assert(sqlFragment.toString)(equalTo(expectedFragment.toString))
        }
      },
      test("PersistenceQuery toSql conversion") {
        check(persistenceQueryGen) { query =>
          val result = query.toSql
          val expected = expectedPersistenceQuerySql(query)
          assert(result.map(_.toString))(equalTo(expected.map(_.toString)))
        }
      },
      test("PersistenceQuery.Condition toSql conversion") {
        check(conditionGen) { condition =>
          val result = condition.toSql
          val expected = expectedConditionSql(condition)
          assert(result.map(_.toString))(equalTo(expected.map(_.toString)))
        }
      },
      test("NonEmptyList[Namespace] toSql conversion") {
        check(namespaceListGen) { namespaces =>
          val result = namespaces.toSql
          val expected = expectedNamespaceSql(namespaces)
          assert(result.toString)(equalTo(expected.toString))
        }
      },
    ),
  )

  def expectedPersistenceQuerySql(query: PersistenceQuery): Option[SqlFragment] = query match {
    case PersistenceQuery.Condition(ns, props, hierarchy, reference) =>
      Seq(
        ns.map(_.toSql),
        hierarchy.map(_.toSql),
        reference.map(_.toSql),
        props.map(_.toSql),
      ).flatten.reduceOption((a, b) => a ++ sql" AND " ++ b) match {
        case Some(a) => (sql"(" ++ a ++ sql")").some
        case _ => None
      }

    case PersistenceQuery.any(condition, more*) =>
      val qs = (condition :: more.toList).reverse.flatMap(_.toSql.toList)
      if qs.isEmpty then None else (sql"(" ++ qs.reduce(_ ++ sql" OR " ++ _) ++ sql")").some
    case PersistenceQuery.forall(query, more*) =>
      val qs = (query :: more.toList).reverse.flatMap(_.toSql.toList)
      if qs.isEmpty then None else (sql"(" ++ qs.reduce(_ ++ sql" AND " ++ _) ++ sql")").some
  }

  def expectedConditionSql(condition: PersistenceQuery.Condition): Option[SqlFragment] = {
    val nsQuery = condition.namespace.fold(SqlFragment.empty)(_.toSql)
    val propsQuery =
      condition.props.fold(List.empty[SqlFragment])(_.map(_.toSql).toList)

    val hierarchyQuery = condition.hierarchy.fold(SqlFragment.empty)(_.toSql)
    val referenceQuery = condition.reference.fold(SqlFragment.empty)(_.toSql)

    val qs = (nsQuery :: hierarchyQuery :: referenceQuery :: propsQuery).filterNot(_.isEmpty)

    if qs.isEmpty then None else (sql"(" ++ qs.reduce(_ ++ sql" AND " ++ _) ++ sql")").some
  }

  def expectedNamespaceSql(namespaces: NonEmptyList[Namespace]): SqlFragment = namespaces match {
    case NonEmptyList.Single(n) => sql"namespace = $n"
    case NonEmptyList.Cons(n, ns) => sql"namespace IN (${n :: ns.toList})"
  }


}

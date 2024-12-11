package com.youtoo
package cqrs
package service
package postgres

import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.jdbc.*

object FetchOptionsSpec extends ZIOSpecDefault, JdbcCodecs {
  def expectedFetchOptionsSql(options: FetchOptions): Option[SqlFragment] = {
    val offsetQuery = options.offset.map(offset => sql" OFFSET $offset ")
    val limitQuery = options.limit.map(limit => sql" LIMIT $limit ")

    (offsetQuery, limitQuery) match {
      case (None, None) => None
      case (Some(l), None) => Some(l)
      case (None, Some(r)) => Some(r)
      case (Some(l), Some(r)) => Some(l ++ r)
    }
  }

  def spec = suite("FetchOptionsSpec")(
    suite("FetchOptions.toSql")(
      test("FetchOptions toSql conversion") {
        check(genFetchOptions) { options =>
          val result = options.toSql
          val expected = expectedFetchOptionsSql(options)
          assert(result)(equalTo(expected))
        }
      },
      test("should return None when both offset and limit are None") {
        val options = FetchOptions(None, None)
        assert(options.toSql)(isNone)
      },
      test("should return SQL fragment with only offset") {
        val options = FetchOptions(Some(Key(10)), None)
        assert(options.toSql.map(_.toString))(isSome(equalTo("Sql( OFFSET ? , 10)")))
      },
      test("should return SQL fragment with only limit") {
        val options = FetchOptions(None, Some(5L))
        assert(options.toSql.map(_.toString))(isSome(equalTo("Sql( LIMIT ? , 5)")))
      },
      test("should return SQL fragment with both offset and limit") {
        val options = FetchOptions(Some(Key(10)), Some(5L))
        assert(options.toSql.map(_.toString))(isSome(equalTo("Sql( OFFSET ?  LIMIT ? , 10, 5)")))
      },
    ),
  )
}

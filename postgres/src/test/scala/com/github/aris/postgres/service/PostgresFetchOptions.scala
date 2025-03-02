package com.github
package aris
package service
package postgres

import zio.*
import zio.test.*
import zio.test.Assertion.*

object PostgresFetchOptionsSpec extends ZIOSpecDefault, JdbcCodecs {
  def expectedFetchOptionsSql(options: FetchOptions): (
    Option[SqlFragment],
    Option[SqlFragment],
    SqlFragment,
  ) = {
    val offsetQuery = options.offset.map(offset =>
      options.order match {
        case FetchOptions.Order.asc => sql"version > $offset"
        case FetchOptions.Order.desc => sql"version < $offset"
      },
    )
    val limitQuery = options.limit.map(limit => sql"LIMIT $limit")
    val orderQuery = options.order match {
      case FetchOptions.Order.asc => sql" ORDER BY version ASC"
      case FetchOptions.Order.desc => sql" ORDER BY version DESC"
    }

    (
      offsetQuery,
      limitQuery,
      orderQuery,
    )
  }

  def spec = suite("PostgresFetchOptionsSpec")(
    suite("FetchOptions.toSql")(
      test("FetchOptions toSql conversion") {
        check(fetchOptionsGen) { options =>
          val result = options.toSql
          val expected = expectedFetchOptionsSql(options)
          assert(result)(equalTo(expected))
        }
      },
      test("should return None when both offset and limit are None") {
        val options = FetchOptions()
        val (f, l, o) = options.toSql
        assert(f)(isNone) && assert(l)(isNone) && assert(o.toString)(equalTo("Sql( ORDER BY version ASC)"))
      },
      test("should return SQL fragment with only offset") {
        val options = FetchOptions(Some(Key(10)), None, FetchOptions.Order.asc)
        val (f, l, o) = options.toSql
        assert(f.map(_.toString))(isSome(equalTo("Sql(version > ?, 10)"))) && assert(l.map(_.toString))(
          isNone,
        ) && assert(o.toString)(equalTo("Sql( ORDER BY version ASC)"))
      },
      test("should return SQL fragment with only limit") {
        val options = FetchOptions(None, Some(5L), FetchOptions.Order.asc)
        val (f, l, o) = options.toSql
        assert(f.map(_.toString))(isNone) && assert(l.map(_.toString))(isSome(equalTo("Sql(LIMIT ?, 5)"))) && assert(
          o.toString,
        )(equalTo("Sql( ORDER BY version ASC)"))
      },
      test("should return SQL fragment with both offset and limit") {
        val options = FetchOptions(Some(Key(10)), Some(5L), FetchOptions.Order.asc)
        val (f, l, o) = options.toSql
        assert(f.map(_.toString))(isSome(equalTo("Sql(version > ?, 10)"))) && assert(l.map(_.toString))(
          isSome(equalTo("Sql(LIMIT ?, 5)")),
        ) && assert(o.toString)(equalTo("Sql( ORDER BY version ASC)"))
      },
    ),
  )
}

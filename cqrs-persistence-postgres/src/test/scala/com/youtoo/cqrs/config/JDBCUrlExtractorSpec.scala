package com.youtoo
package cqrs
package config

import com.youtoo.postgres.config.*

import zio.test.*
import zio.test.Assertion.*

object JDBCUrlExtractorSpec extends ZIOSpecDefault {

  def spec = suite("JDBCUrlExtractorSpec")(
    test("should extract host, port, and database name from a valid JDBC URL") {
      val jdbcUrl = "jdbc:postgresql://localhost:5432/mydatabase"

      val result = jdbcUrl match {
        case DatabaseConfig.JDBCUrlExtractor(host, port, dbName) =>
          assert(host)(equalTo("localhost")) &&
          assert(port)(equalTo(5432)) &&
          assert(dbName)(equalTo("mydatabase"))
        case _ =>
          assert(false)(isTrue)
      }
      result
    },
    test("should extract host, port, and database name from a JDBC URL with complex host") {
      val jdbcUrl = "jdbc:mysql://db-server.example.com:3306/test_db"

      val result = jdbcUrl match {
        case DatabaseConfig.JDBCUrlExtractor(host, port, dbName) =>
          assert(host)(equalTo("db-server.example.com")) &&
          assert(port)(equalTo(3306)) &&
          assert(dbName)(equalTo("test_db"))
        case _ =>
          assert(false)(isTrue)
      }
      result
    },
    test("should not match an invalid JDBC URL") {
      val invalidJdbcUrl = "invalid:postgresql://localhost:5432/mydatabase"

      val result = invalidJdbcUrl match {
        case DatabaseConfig.JDBCUrlExtractor(_, _, _) =>
          assert(false)(isTrue)
        case _ =>
          assertCompletes
      }
      result
    },
    test("should not match a URL missing the database name") {
      val invalidJdbcUrl = "jdbc:postgresql://localhost:5432/"

      val result = invalidJdbcUrl match {
        case DatabaseConfig.JDBCUrlExtractor(_, _, _) =>
          assert(false)(isTrue)
        case _ =>
          assertCompletes
      }
      result
    },
  )
}

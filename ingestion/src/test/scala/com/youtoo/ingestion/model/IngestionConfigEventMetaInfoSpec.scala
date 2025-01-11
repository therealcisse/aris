package com.youtoo
package ingestion
package model

import zio.test.*
import zio.test.Assertion.*

object IngestionConfigEventMetaInfoSpec extends ZIOSpecDefault {

  val spec = suite("IngestionConfigEventMetaInfoSpec")(
    test("ConnectionAdded should have the correct namespace") {
      check(connectionGen) { connection =>
        val event = IngestionConfigEvent.ConnectionAdded(connection)
        assert(event.namespace)(equalTo(IngestionConfigEvent.NS.ConnectionAdded))
      }
    },
    test("All events should have an empty hierarchy") {
      check(ingestionConfigEventGen) { event =>
        assert(event.hierarchy)(isNone)
      }
    },
    test("All events should have empty props") {
      check(ingestionConfigEventGen) { event =>
        assert(event.props)(isEmpty)
      }
    },
    test("ConnectionAdded should have a reference to its connection id") {
      check(connectionGen) { connection =>
        val event = IngestionConfigEvent.ConnectionAdded(connection)
        assert(event.reference)(isSome(equalTo(connection.id)))
      }
    },
  )
}

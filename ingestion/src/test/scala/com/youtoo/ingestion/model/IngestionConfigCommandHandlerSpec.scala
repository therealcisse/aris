package com.youtoo
package ingestion
package model

import com.youtoo.cqrs.*
import com.youtoo.sink.*
import com.youtoo.source.*

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*

object IngestionConfigCommandHandlerSpec extends ZIOSpecDefault {
  val handler = summon[CmdHandler[IngestionConfigCommand, IngestionConfigEvent]]

  def spec = suite("IngestionConfigCommandHandlerSpec")(
    test("AddConnection command produces ConnectionAdded event") {
      check(
        connectionIdGen,
        sourceIdGen,
        (sinkIdGen <*> Gen.listOf(sinkIdGen)).map((e, es) => NonEmptySet(e, es*)),
      ) { (connectionId, source, sinks) =>
        val command = IngestionConfigCommand.AddConnection(connectionId, source, sinks)
        val events = handler.applyCmd(command)
        val expectedEvent =
          IngestionConfigEvent.ConnectionAdded(IngestionConfig.Connection(connectionId, source, sinks))
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
  )
}

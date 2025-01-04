package com.youtoo
package sink
package model

import com.youtoo.cqrs.*

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*

object SinkCommandHandlerSpec extends ZIOSpecDefault {
  val handler = summon[CmdHandler[SinkCommand, SinkEvent]]

  def spec = suite("SinkCommandHandlerSpec")(
    test("AddOrModify command produces Added event") {
      check(sinkIdGen, sinkTypeGen) { (id, info) =>
        val command = SinkCommand.AddOrModify(id, info)
        val events = handler.applyCmd(command)
        val expectedEvent = SinkEvent.Added(id, info)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("Delete command produces Deleted event") {
      check(sinkIdGen) { id =>
        val command = SinkCommand.Delete(id)
        val events = handler.applyCmd(command)
        val expectedEvent = SinkEvent.Deleted(id)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
  )
}

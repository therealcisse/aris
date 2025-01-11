package com.youtoo
package source
package model

import com.youtoo.cqrs.*

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*

object SourceCommandHandlerSpec extends ZIOSpecDefault {
  val handler = summon[CmdHandler[SourceCommand, SourceEvent]]

  def spec = suite("SourceCommandHandlerSpec")(
    test("AddOrModify command produces Added event") {
      check(sourceIdGen, sourceTypeGen) { (id, info) =>
        val command = SourceCommand.AddOrModify(id, info)
        val events = handler.applyCmd(command)
        val expectedEvent = SourceEvent.Added(id, info)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("Delete command produces Deleted event") {
      check(sourceIdGen) { id =>
        val command = SourceCommand.Delete(id)
        val events = handler.applyCmd(command)
        val expectedEvent = SourceEvent.Deleted(id)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
  )
}

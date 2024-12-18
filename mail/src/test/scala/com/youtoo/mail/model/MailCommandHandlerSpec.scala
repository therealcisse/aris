package com.youtoo
package mail
package model

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*

import com.youtoo.cqrs.*

object MailCommandHandlerSpec extends ZIOSpecDefault {
  val handler = summon[CmdHandler[MailCommand, MailEvent]]

  def spec = suite("MailCommandHandlerSpec")(
    test("StartSync command produces SyncStarted event") {
      check(mailLabelsGen, timestampGen, jobIdGen) { (labels, timestamp, jobId) =>
        val command = MailCommand.StartSync(labels, timestamp, jobId)
        val events = handler.applyCmd(command)
        val expectedEvent = MailEvent.SyncStarted(labels = labels, timestamp = timestamp, jobId = jobId)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("RecordSync command produces MailSynced event") {
      check(timestampGen, Gen.listOf(mailDataIdGen), Gen.option(mailTokenGen), jobIdGen) {
        (timestamp, keys, token, jobId) =>
          val command = MailCommand.RecordSync(timestamp, keys, token, jobId)
          val events = handler.applyCmd(command)
          val expectedEvent = MailEvent.MailSynced(timestamp = timestamp, mailKeys = keys, token = token, jobId = jobId)
          assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
    test("Applying the same command multiple times produces the same event") {
      check(timestampGen, Gen.listOf(mailDataIdGen), Gen.option(mailTokenGen), jobIdGen) {
        (timestamp, keys, token, jobId) =>
          val command = MailCommand.RecordSync(timestamp, keys, token, jobId)
          val events1 = handler.applyCmd(command)
          val events2 = handler.applyCmd(command)
          assert(events1)(equalTo(events2))
      }
    },
    test("CompleteSync command produces SyncCompleted event") {
      check(timestampGen, jobIdGen) { (timestamp, jobId) =>
        val command = MailCommand.CompleteSync(timestamp, jobId)
        val events = handler.applyCmd(command)
        val expectedEvent = MailEvent.SyncCompleted(timestamp = timestamp, jobId = jobId)
        assert(events)(equalTo(NonEmptyList(expectedEvent)))
      }
    },
  ) @@ TestAspect.withLiveClock
}

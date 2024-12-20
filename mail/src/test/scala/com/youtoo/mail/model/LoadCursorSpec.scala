package com.youtoo
package mail
package model

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*

import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*

object LoadCursorSpec extends ZIOSpecDefault {

  def spec = suite("LoadCursorSpec")(
    test("should load Cursor from events") {
      check(mailSyncedChangeGen, Gen.listOfN(5)(mailSyncedChangeGen)) { (e, es) =>
        val handler = new MailEvent.LoadCursor()
        val events = NonEmptyList(e, es*).reverse
        val result = handler.applyEvents(events)
        val payload = e.payload.asInstanceOf[MailEvent.MailSynced]
        val total = events.toList.foldLeft(0) {
          case (acc, Change(_, MailEvent.MailSynced(_, keys, _, _))) => acc + keys.size
          case (acc, _) => acc
        }
        assert(result)(
          equalTo(Some(Cursor(payload.timestamp, payload.token, total = TotalMessages(total), isSyncing = true))),
        )
      }
    },
    test("should load sync status correctly") {
      check(validMailEventSequenceGen(isCompleted = true)) { (events) =>
        val handler = new MailEvent.LoadCursor()
        val result = handler.applyEvents(events)
        assert(result.map(_.isSyncing))(isNone) || assert(result.map(_.isSyncing))(equalTo(Some(false)))
      }
    },
    test("should return None if no MailSynced events") {
      check(
        Gen.oneOf(
          syncCompletedChangeGen,
          syncStartedChangeGen,
        ),
      ) { ch =>
        val handler = new MailEvent.LoadCursor()
        val events = NonEmptyList(ch)
        val result = handler.applyEvents(events)
        assert(result)(isNone)
      }
    },
  )
}

package com.youtoo
package mail
package model

import zio.test.*
import zio.test.Assertion.*
import zio.*
import zio.prelude.*

import com.youtoo.cqrs.*

object MailEventMetaInfoSpec extends ZIOSpecDefault {

  def spec = suite("MailEventMetaInfoSpec")(
    test("MetaInfo[MailEvent] - SyncStarted") {
      check(mailLabelsGen, timestampGen, jobIdGen) { (labels, timestamp, jobId) =>
        val event = MailEvent.SyncStarted(labels, timestamp, jobId)
        val expectedNamespace = Namespace(0)
        val expectedHierarchy = None

        val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(isEmpty)

        namespaceAssertion && hierarchyAssertion && propsAssertion
      }
    },
    test("MetaInfo[MailEvent] - MailSynced") {
      check(timestampGen, mailDataIdGen, Gen.listOf(mailDataIdGen), mailTokenGen, jobIdGen) {
        (timestamp, key, mailKeys, token, jobId) =>
          val event = MailEvent.MailSynced(timestamp, NonEmptyList(key, mailKeys*), token, jobId)
          val expectedNamespace = Namespace(1)
          val expectedHierarchy = None

          val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
          val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
          val propsAssertion = assert(event.props)(isEmpty)

          namespaceAssertion && hierarchyAssertion && propsAssertion
      }
    },
    test("MetaInfo[MailEvent] - SyncCompleted") {
      check(timestampGen, jobIdGen) { (timestamp, jobId) =>
        val event = MailEvent.SyncCompleted(timestamp, jobId)
        val expectedNamespace = Namespace(2)
        val expectedHierarchy = None

        val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(isEmpty)

        namespaceAssertion && hierarchyAssertion && propsAssertion
      }
    },
  )
}

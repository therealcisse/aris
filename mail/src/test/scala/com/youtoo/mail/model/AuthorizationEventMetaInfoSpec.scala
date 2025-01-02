package com.youtoo
package mail
package model

import zio.test.*
import zio.test.Assertion.*
import zio.*

import com.youtoo.cqrs.*

object AuthorizationEventMetaInfoSpec extends ZIOSpecDefault {

  def spec = suite("AuthorizationEventMetaInfoSpec")(
    test("MetaInfo[AuthorizationEvent] - AuthorizationGranted") {
      check(tokenInfoGen, timestampGen) { (token, timestamp) =>
        val event = AuthorizationEvent.AuthorizationGranted(token, timestamp)
        val expectedNamespace = Namespace(0)
        val expectedHierarchy = None

        val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(isEmpty)

        namespaceAssertion && hierarchyAssertion && propsAssertion
      }
    },
    test("MetaInfo[AuthorizationEvent] - AuthorizationRevoked") {
      check(timestampGen) { (timestamp) =>
        val event = AuthorizationEvent.AuthorizationRevoked(timestamp)
        val expectedNamespace = Namespace(100)
        val expectedHierarchy = None

        val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(isEmpty)

        namespaceAssertion && hierarchyAssertion && propsAssertion
      }
    },
  )
}

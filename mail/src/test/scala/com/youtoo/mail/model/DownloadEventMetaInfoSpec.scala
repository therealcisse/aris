package com.youtoo
package mail
package model

import cats.implicits.*

import zio.test.*
import zio.test.Assertion.*
import zio.*

import com.youtoo.cqrs.*

object DownloadEventMetaInfoSpec extends ZIOSpecDefault {

  def spec = suite("DownloadEventMetaInfoSpec")(
    test("MetaInfo[DownloadEvent] - Downloaded") {
      check(versionGen, jobIdGen) { (version, jobId) =>
        val event = DownloadEvent.Downloaded(version, jobId)
        val expectedNamespace = Namespace(0)
        val expectedHierarchy = Hierarchy.Child(jobId.asKey).some

        val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(isEmpty)

        namespaceAssertion && hierarchyAssertion && propsAssertion
      }
    },
  )
}

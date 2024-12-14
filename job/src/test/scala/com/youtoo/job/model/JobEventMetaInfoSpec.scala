package com.youtoo
package job
package model

import zio.test.*
import zio.test.Assertion.*
import zio.*
import com.youtoo.cqrs.*

object JobEventMetaInfoSpec extends ZIOSpecDefault {

  def spec = suite("JobEventMetaInfoSpec")(
    test("MetaInfo[JobEvent] - JobStarted") {
      check(jobIdGen, timestampGen, jobMeasurementGen, jobTagGen) { (id, timestamp, total, tag) =>
        val event = JobEvent.JobStarted(id, timestamp, total, tag)
        val expectedNamespace = Namespace(0)
        val expectedHierarchy = None

        val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(isEmpty)
        val referenceAssertion = assert(event.reference)(isNone)

        namespaceAssertion && hierarchyAssertion && propsAssertion && referenceAssertion
      }
    },
    test("MetaInfo[JobEvent] - ProgressReported") {
      check(jobIdGen, timestampGen, progressGen) { (id, timestamp, progress) =>
        val event = JobEvent.ProgressReported(id, timestamp, progress)
        val expectedNamespace = Namespace(1)
        val expectedHierarchy = None

        val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(isEmpty)
        val referenceAssertion = assert(event.reference)(isNone)

        namespaceAssertion && hierarchyAssertion && propsAssertion && referenceAssertion
      }
    },
    test("MetaInfo[JobEvent] - JobCompleted") {
      check(jobIdGen, timestampGen, jobCompletionReasonGen) { (id, timestamp, reason) =>
        val event = JobEvent.JobCompleted(id, timestamp, reason)
        val expectedNamespace = Namespace(2)
        val expectedHierarchy = None

        val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(isEmpty)
        val referenceAssertion = assert(event.reference)(isNone)

        namespaceAssertion && hierarchyAssertion && propsAssertion && referenceAssertion
      }
    },
  )
}

package com.youtoo
package ingestion
package model

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*
import zio.*
import com.youtoo.cqrs.*

object IngestionEventMetaInfoSpec extends ZIOSpecDefault {

  def spec = suite("IngestionEventMetaInfoSpec")(
    test("MetaInfo[IngestionEvent] - IngestionStarted") {
      check(ingestionIdGen, timestampGen) { (id, timestamp) =>
        val event = IngestionEvent.IngestionStarted(id, timestamp)
        val expectedNamespace = Namespace(0)
        val expectedHierarchy = None

        val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(isEmpty)

        namespaceAssertion && hierarchyAssertion && propsAssertion
      }
    },
    test("MetaInfo[IngestionEvent] - IngestionFilesResolved") {
      check(fileIdGen) { file =>
        val event = IngestionEvent.IngestionFilesResolved(NonEmptySet(file))
        val expectedNamespace = Namespace(100)
        val expectedHierarchy = None

        val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(isEmpty)

        namespaceAssertion && hierarchyAssertion && propsAssertion
      }
    },
    test("MetaInfo[IngestionEvent] - IngestionFileProcessing") {
      check(fileIdGen) { file =>
        val event = IngestionEvent.IngestionFileProcessing(file)
        val expectedNamespace = Namespace(200)
        val expectedHierarchy = None

        val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(isEmpty)

        namespaceAssertion && hierarchyAssertion && propsAssertion
      }
    },
    test("MetaInfo[IngestionEvent] - IngestionFileProcessed") {
      check(fileIdGen) { file =>
        val event = IngestionEvent.IngestionFileProcessed(file)
        val expectedNamespace = Namespace(300)
        val expectedHierarchy = None

        val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(isEmpty)

        namespaceAssertion && hierarchyAssertion && propsAssertion
      }
    },
    test("MetaInfo[IngestionEvent] - IngestionFileFailed") {
      check(fileIdGen) { file =>
        val event = IngestionEvent.IngestionFileFailed(file)
        val expectedNamespace = Namespace(400)
        val expectedHierarchy = None

        val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(isEmpty)

        namespaceAssertion && hierarchyAssertion && propsAssertion
      }
    },
    test("MetaInfo[IngestionEvent] - IngestionCompleted") {
      check(timestampGen) { timestamp =>
        val event = IngestionEvent.IngestionCompleted(timestamp)
        val expectedNamespace = Namespace(500)
        val expectedHierarchy = None

        val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(isEmpty)

        namespaceAssertion && hierarchyAssertion && propsAssertion
      }
    },
  )
}

package com.youtoo
package migration
package model

import zio.*
import zio.test.*
import zio.test.Assertion.*
import com.youtoo.cqrs.*

object MigrationEventMetaInfoSpec extends ZIOSpecDefault {

  def spec = suite("MigrationEventMetaInfoSpec")(
    test("MetaInfo[MigrationEvent] - MigrationRegistered") {
      check(migrationIdGen, timestampGen) { (id, timestamp) =>
        val event = MigrationEvent.MigrationRegistered(id, timestamp)
        val expectedNamespace = Namespace(0)
        val expectedHierarchy = None

        val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(isEmpty)

        namespaceAssertion && hierarchyAssertion && propsAssertion
      }
    },
    test("MetaInfo[MigrationEvent] - ExecutionStarted") {
      check(executionIdGen, timestampGen) { (id, timestamp) =>
        val event = MigrationEvent.ExecutionStarted(id, timestamp)
        val expectedNamespace = Namespace(100)
        val expectedHierarchy = None

        val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(isEmpty)

        namespaceAssertion && hierarchyAssertion && propsAssertion
      }
    },
    test("MetaInfo[MigrationEvent] - ProcessingStarted") {
      check(executionIdGen, keyGen) { (id, key) =>
        val event = MigrationEvent.ProcessingStarted(id, key)
        val expectedNamespace = Namespace(200)
        val expectedHierarchy = Some(Hierarchy.Child(id.asKey))

        val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(isEmpty)

        namespaceAssertion && hierarchyAssertion && propsAssertion
      }
    },
    test("MetaInfo[MigrationEvent] - KeyProcessed") {
      check(executionIdGen, keyGen) { (id, key) =>
        val event = MigrationEvent.KeyProcessed(id, key)
        val expectedNamespace = Namespace(300)
        val expectedHierarchy = Some(Hierarchy.Child(id.asKey))

        val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(isEmpty)

        namespaceAssertion && hierarchyAssertion && propsAssertion
      }
    },
    test("MetaInfo[MigrationEvent] - ProcessingFailed") {
      check(executionIdGen, keyGen) { (id, key) =>
        val event = MigrationEvent.ProcessingFailed(id, key)
        val expectedNamespace = Namespace(400)
        val expectedHierarchy = Some(Hierarchy.Child(id.asKey))

        val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(isEmpty)

        namespaceAssertion && hierarchyAssertion && propsAssertion
      }
    },
    test("MetaInfo[MigrationEvent] - ExecutionStopped") {
      check(executionIdGen, timestampGen) { (id, timestamp) =>
        val event = MigrationEvent.ExecutionStopped(id, timestamp)
        val expectedNamespace = Namespace(500)
        val expectedHierarchy = None

        val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(isEmpty)

        namespaceAssertion && hierarchyAssertion && propsAssertion
      }
    },
    test("MetaInfo[MigrationEvent] - ExecutionFinished") {
      check(executionIdGen, timestampGen) { (id, timestamp) =>
        val event = MigrationEvent.ExecutionFinished(id, timestamp)
        val expectedNamespace = Namespace(600)
        val expectedHierarchy = None

        val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(isEmpty)

        namespaceAssertion && hierarchyAssertion && propsAssertion
      }
    },
    test("MetaInfo[MigrationEvent] - ExecutionFailed") {
      check(executionIdGen, timestampGen) { (id, timestamp) =>
        val event = MigrationEvent.ExecutionFailed(id, timestamp)
        val expectedNamespace = Namespace(700)
        val expectedHierarchy = None

        val namespaceAssertion = assert(event.namespace)(equalTo(expectedNamespace))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(isEmpty)

        namespaceAssertion && hierarchyAssertion && propsAssertion
      }
    },
  )
}

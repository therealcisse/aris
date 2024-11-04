package com.youtoo
package ingestion
package model

import zio.test.*
import zio.test.Assertion.*
import zio.*
import com.youtoo.cqrs.*

object FileEventMetaInfoSpec extends ZIOSpecDefault {

  def spec = suite("FileEventMetaInfoSpec")(
    test("MetaInfo[FileEvent] - FileAdded event") {
      check(providerIdGen, ingestionFileIdGen, ingestionFileNameGen, ingestionFileSigGen, ingestionFileMetadataGen) {
        (providerId, fileId, fileName, fileSig, metadata) =>
          val event = FileEvent.FileAdded(
            provider = providerId,
            id = fileId,
            name = fileName,
            metadata = metadata,
            sig = fileSig,
          )

          val expectedHierarchy = Some(Hierarchy.Child(providerId.asKey))
          val expectedProps = Chunk(
            EventProperty("sig", fileSig.value),
            EventProperty("name", fileName.value),
          )

          val namespaceAssertion = assert(event.namespace)(equalTo(Namespace(0)))
          val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
          val propsAssertion = assert(event.props)(equalTo(expectedProps))

          namespaceAssertion && hierarchyAssertion && propsAssertion
      }
    },
    test("MetaInfo[FileEvent] - ProviderAdded event") {
      check(providerIdGen, providerNameGen, providerLocationGen) { (providerId, providerName, providerLocation) =>
        val event = FileEvent.ProviderAdded(
          id = providerId,
          name = providerName,
          location = providerLocation,
        )

        val expectedHierarchy = None
        val expectedProps = providerLocation match {
          case Provider.Location.File(path) =>
            Chunk(
              EventProperty("name", providerName.value),
              EventProperty("locationType", "File"),
              EventProperty("location", path),
            )
          // Handle other location types if applicable
        }

        val namespaceAssertion = assert(event.namespace)(equalTo(Namespace(1)))
        val hierarchyAssertion = assert(event.hierarchy)(equalTo(expectedHierarchy))
        val propsAssertion = assert(event.props)(equalTo(expectedProps))

        namespaceAssertion && hierarchyAssertion && propsAssertion
      }
    },
  )
}

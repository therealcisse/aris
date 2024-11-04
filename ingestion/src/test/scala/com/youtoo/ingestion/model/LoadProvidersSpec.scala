package com.youtoo
package ingestion
package model

import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*
import zio.*
import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.Codecs.given

object LoadProvidersSpec extends ZIOSpecDefault {

  def spec = suite("LoadProvidersSpec")(
    test("should load all Providers from events") {
      check(Gen.listOfN(5)(providerGen), versionGen, providerIdGen, providerNameGen, providerLocationGen) {
        (providers, version, dummyProviderId, dummyProviderName, dummyProviderLocation) =>
          val handler = new FileEvent.LoadProviders()

          val events = NonEmptyList
            .fromIterableOption(
              providers.map { provider =>
                Change(
                  version,
                  FileEvent.ProviderAdded(
                    id = provider.id,
                    name = provider.name,
                    location = provider.location,
                  ),
                )
              }.toList,
            )
            .getOrElse(
              NonEmptyList(
                Change(
                  version,
                  FileEvent.ProviderAdded(
                    id = dummyProviderId,
                    name = dummyProviderName,
                    location = dummyProviderLocation,
                  ),
                ),
              ),
            )

          val result = handler.applyEvents(events)

          assert(result.toSet)(equalTo(providers.toSet))
      }
    },
    test("should return empty list if no ProviderAdded events") {
      check(versionGen, fileMetadataGen, providerIdGen, fileIdGen, fileNameGen, fileSigGen) {
        (version, metadata, providerId, fileId, fileName, fileSig) =>
          val handler = new FileEvent.LoadProviders()

          val events = NonEmptyList(
            Change(
              version,
              FileEvent.FileAdded(
                provider = providerId,
                id = fileId,
                name = fileName,
                metadata = metadata,
                sig = fileSig,
              ),
            ),
          )

          val result = handler.applyEvents(events)

          assert(result)(isEmpty)
      }
    },
  )
}

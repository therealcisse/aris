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
      check(Gen.listOfN(5)(providerGen), versionGen) { (providers, version) =>
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
                  id = Provider.Id("dummy"),
                  name = Provider.Name("dummy"),
                  location = Provider.Location.File("dummy"),
                ),
              ),
            ),
          )

        val result = handler.applyEvents(events)

        assert(result.toSet)(equalTo(providers.toSet))
      }
    },
    test("should return empty list if no ProviderAdded events") {
      check(versionGen) { (version) =>
        val handler = new FileEvent.LoadProviders()

        val events = NonEmptyList(
          Change(
            version,
            FileEvent.FileAdded(
              provider = Provider.Id("provider-1"),
              id = IngestionFile.Id("file-1"),
              name = IngestionFile.Name("file-name"),
              metadata = IngestionFile.Metadata(),
              sig = IngestionFile.Sig("signature"),
            ),
          ),
        )

        val result = handler.applyEvents(events)

        assert(result)(isEmpty)
      }
    },
  )
}

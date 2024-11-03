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

object LoadProviderSpec extends ZIOSpecDefault {

  def spec = suite("LoadProviderSpec")(
    test("should load Provider by ID from events") {
      check(providerIdGen, providerNameGen, providerLocationGen, versionGen) {
        (providerId, providerName, providerLocation, version) =>
          val handler = new FileEvent.LoadProvider(providerId)

          val events = NonEmptyList(
            Change(
              version,
              FileEvent.ProviderAdded(
                id = providerId,
                name = providerName,
                location = providerLocation,
              ),
            ),
          )

          val result = handler.applyEvents(events)

          val expectedProvider = Provider(
            id = providerId,
            name = providerName,
            location = providerLocation,
          )

          assert(result)(isSome(equalTo(expectedProvider)))
      }
    },
    test("should return None if provider with given ID is not in events") {
      check(providerIdGen, providerIdGen, providerNameGen, providerLocationGen, versionGen) {
        (providerId, differentProviderId, providerName, providerLocation, version) =>
          val handler = new FileEvent.LoadProvider(providerId)

          val events = NonEmptyList(
            Change(
              version,
              FileEvent.ProviderAdded(
                id = differentProviderId, // Different ID
                name = providerName,
                location = providerLocation,
              ),
            ),
          )

          val result = handler.applyEvents(events)

          val expectedProvider = Provider(
            id = providerId,
            name = providerName,
            location = providerLocation,
          )

          assert(result)(if providerId == differentProviderId then isSome(equalTo(expectedProvider)) else isNone)
      }
    },
  )
}

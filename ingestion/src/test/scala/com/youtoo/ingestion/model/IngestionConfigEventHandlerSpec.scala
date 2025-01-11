package com.youtoo
package ingestion
package model

import com.youtoo.source.*
import com.youtoo.sink.*
import com.youtoo.cqrs.*
import com.youtoo.cqrs.domain.*

import com.youtoo.cqrs.Codecs.given

import zio.*
import zio.test.*
import zio.test.Assertion.*
import zio.prelude.*

object IngestionConfigEventHandlerSpec extends ZIOSpecDefault {

  val spec = suite("IngestionConfigEventHandlerSpec")(
    test("LoadIngestionConfig should apply ConnectionAdded event") {
      check(
        connectionIdGen,
        sourceIdGen,
        (sinkIdGen <*> Gen.listOf(sinkIdGen)).map((e, es) => NonEmptySet(e, es*)),
      ) { (connectionId, sourceId, sinkIds) =>
        val event: IngestionConfigEvent =
          IngestionConfigEvent.ConnectionAdded(IngestionConfig.Connection(connectionId, sourceId, sinkIds))
        val events = NonEmptyList(Change(Version(1L), event))
        val expected = IngestionConfig(
          Chunk(IngestionConfig.Connection(connectionId, sourceId, sinkIds)),
        )
        val result = EventHandler.applyEvents(events)(using IngestionConfigEvent.LoadIngestionConfig())
        assert(result)(isSome(equalTo(expected)))
      }
    },
    test("LoadIngestionConfig should apply multiple ConnectionAdded events") {
      check(
        ingestionConfigEventChangeGen,
        Gen.listOf(ingestionConfigEventChangeGen),
      ) { (e, es) =>
        val events = NonEmptyList(e, es*)
        val expected = IngestionConfig(
          (e.payload.asInstanceOf[IngestionConfigEvent.ConnectionAdded].connection :: es.map(
            _.payload.asInstanceOf[IngestionConfigEvent.ConnectionAdded].connection,
          )).toChunk,
        )
        val result = EventHandler.applyEvents(events)(using IngestionConfigEvent.LoadIngestionConfig())
        assert(result)(isSome(equalTo(expected)))
      }
    },
    test("LoadIngestionConfig should apply the same ConnectionAdded event multiple times resulting in the same state") {
      check(
        connectionIdGen,
        sourceIdGen,
        (sinkIdGen <*> Gen.listOf(sinkIdGen)).map((e, es) => NonEmptySet(e, es*)),
      ) { (connectionId, sourceId, sinkIds) =>
        val event: IngestionConfigEvent =
          IngestionConfigEvent.ConnectionAdded(IngestionConfig.Connection(connectionId, sourceId, sinkIds))
        val events = NonEmptyList(
          Change(Version(1L), event),
          List(
            Change(Version(2L), event),
            Change(Version(3L), event),
          )*,
        )
        val expected = IngestionConfig(
          Chunk(IngestionConfig.Connection(connectionId, sourceId, sinkIds)),
        )
        val result = EventHandler.applyEvents(events)(using IngestionConfigEvent.LoadIngestionConfig())
        assert(result)(isSome(equalTo(expected)))
      }
    },
  )
}

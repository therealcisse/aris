package com.github
package aris
package service
package memory

import com.github.aris.service.*

import com.github.aris.domain.*

import com.github.aris.Codecs.given

import cats.implicits.*

import zio.*
import zio.prelude.*
import zio.test.*
import zio.test.Assertion.*
import zio.schema.*

object MemoryCQRSPersistenceSpec extends ZIOSpecDefault {
  case class DummyEvent(id: String)

  inline val grandParentIdXXX = 11L
  inline val parentIdXXX = 22L

  object DummyEvent {
    given Schema[DummyEvent] = DeriveSchema.gen[DummyEvent]

  }

  given MetaInfo[DummyEvent]  {
    extension (self: DummyEvent) def namespace: Namespace = Namespace(0)
    extension (self: DummyEvent) def hierarchy: Option[Hierarchy] = Hierarchy.Descendant(Key(1L), Key(2L)).some
    extension (self: DummyEvent) def props: Chunk[EventProperty] = Chunk(EventProperty("type", "DummyEvent"))
    extension (self: DummyEvent) def reference: Option[ReferenceKey] = None
    extension (self: DummyEvent) def timestamp: Option[Timestamp] = None

  }

  def spec =
    suite("MemoryCQRSPersistenceSpec")(
      test("should save and retrieve events correctly by hierarchy : Descendant") {
        check(namespaceGen, keyGen, versionGen, keyGen, keyGen, discriminatorGen, dummyEventGen) {
          (ns, key, version, grandParentId, parentId, discriminator, event) =>
            given MetaInfo[DummyEvent]  {
              extension (self: DummyEvent) def namespace: Namespace = ns
              extension (self: DummyEvent)
                def hierarchy: Option[Hierarchy] = Hierarchy.Descendant(grandParentId, parentId).some
              extension (self: DummyEvent) def props: Chunk[EventProperty] = Chunk()
              extension (self: DummyEvent) def reference: Option[ReferenceKey] = None
              extension (self: DummyEvent) def timestamp: Option[Timestamp] = None

            }

            for {
              persistence <- ZIO.service[CQRSPersistence]

              ch = Change(version = version, event)

              saveResult <- (persistence.saveEvent(key, discriminator, ch, Catalog.Default))

              a = assert(saveResult)(equalTo(1L))

              events0 <- (persistence.readEvents[DummyEvent](key, discriminator, Catalog.Default))
              b = assert(events0)(isNonEmpty)

              c <- (
                for {
                  events0 <- (persistence.readEvents[DummyEvent](key, discriminator, Catalog.Default))
                  events1 <- (
                    persistence.readEvents[DummyEvent](
                      discriminator,
                      query = PersistenceQuery.hierarchy(Hierarchy.Descendant(grandParentId, parentId)),
                      options = FetchOptions(),
                      catalog = Catalog.Default,
                    )
                  )
                  events2 <- (
                    persistence.readEvents[DummyEvent](
                      discriminator,
                      query = PersistenceQuery.hierarchy(Hierarchy.Descendant(Key(grandParentIdXXX), parentId)),
                      options = FetchOptions(),
                      catalog = Catalog.Default,
                    )
                  )
                  events3 <- (
                    persistence.readEvents[DummyEvent](
                      discriminator,
                      query = PersistenceQuery.hierarchy(Hierarchy.Descendant(grandParentId, Key(parentIdXXX))),
                      options = FetchOptions(),
                      catalog = Catalog.Default,
                    )
                  )

                } yield assert(events0)(isNonEmpty) && assert(events1)(isNonEmpty) && assert(events2)(
                  isEmpty,
                ) && assert(
                  events3,
                )(isEmpty) && assert(
                  events1,
                )(hasSubset(events0))
              )

            } yield a && b && c
        }
      },
      test("should save and retrieve events correctly by hierarchy : Child") {
        check(namespaceGen, keyGen, versionGen, keyGen, keyGen, discriminatorGen, dummyEventGen) {
          (ns, key, version, grandParentId, parentId, discriminator, event) =>
            given MetaInfo[DummyEvent]  {
              extension (self: DummyEvent) def namespace: Namespace = ns
              extension (self: DummyEvent)
                def hierarchy: Option[Hierarchy] = Hierarchy.Descendant(grandParentId, parentId).some
              extension (self: DummyEvent) def props: Chunk[EventProperty] = Chunk(EventProperty("type", "DummyEvent"))
              extension (self: DummyEvent) def reference: Option[ReferenceKey] = None
              extension (self: DummyEvent) def timestamp: Option[Timestamp] = None

            }

            for {
              persistence <- ZIO.service[CQRSPersistence]

              ch = Change(version = version, event)

              saveResult <- (persistence.saveEvent(key, discriminator, ch, Catalog.Default))

              a = assert(saveResult)(equalTo(1L))

              events0 <- (persistence.readEvents[DummyEvent](key, discriminator, Catalog.Default))
              b = assert(events0)(isNonEmpty)

              e <- (
                for {
                  events0 <- (persistence.readEvents[DummyEvent](key, discriminator, Catalog.Default))
                  events1 <- (
                    persistence.readEvents[DummyEvent](
                      discriminator,
                      query = PersistenceQuery.hierarchy(Hierarchy.Child(parentId)),
                      options = FetchOptions(),
                      catalog = Catalog.Default,
                    )
                  )
                  events2 <- (
                    persistence.readEvents[DummyEvent](
                      discriminator,
                      query = PersistenceQuery.hierarchy(Hierarchy.Child(Key(parentIdXXX))),
                      options = FetchOptions(),
                      catalog = Catalog.Default,
                    )
                  )

                } yield assert(events0)(isNonEmpty) && assert(events1)(isNonEmpty) && assert(events2)(
                  isEmpty,
                ) && assert(
                  events1,
                )(hasSubset(events0))
              )

            } yield a && b && e
        }
      },
      test("should save and retrieve events correctly by hierarchy : GrandChild") {
        check(namespaceGen, keyGen, versionGen, keyGen, keyGen, discriminatorGen, dummyEventGen) {
          (ns, key, version, grandParentId, parentId, discriminator, event) =>
            given MetaInfo[DummyEvent]  {
              extension (self: DummyEvent) def namespace: Namespace = ns
              extension (self: DummyEvent)
                def hierarchy: Option[Hierarchy] = Hierarchy.Descendant(grandParentId, parentId).some
              extension (self: DummyEvent) def props: Chunk[EventProperty] = Chunk(EventProperty("type", "DummyEvent"))
              extension (self: DummyEvent) def reference: Option[ReferenceKey] = None
              extension (self: DummyEvent) def timestamp: Option[Timestamp] = None

            }

            for {
              persistence <- ZIO.service[CQRSPersistence]

              ch = Change(version = version, event)

              saveResult <- (persistence.saveEvent(key, discriminator, ch, Catalog.Default))

              a = assert(saveResult)(equalTo(1L))

              events0 <- (persistence.readEvents[DummyEvent](key, discriminator, Catalog.Default))
              b = assert(events0)(isNonEmpty)

              d <- (
                for {
                  events0 <- (persistence.readEvents[DummyEvent](key, discriminator, Catalog.Default))
                  events1 <- (
                    persistence.readEvents[DummyEvent](
                      discriminator,
                      query = PersistenceQuery.hierarchy(Hierarchy.GrandChild(grandParentId)),
                      options = FetchOptions(),
                      catalog = Catalog.Default,
                    )
                  )
                  events2 <- (
                    persistence.readEvents[DummyEvent](
                      discriminator,
                      query = PersistenceQuery.hierarchy(Hierarchy.GrandChild(Key(grandParentIdXXX))),
                      options = FetchOptions(),
                      catalog = Catalog.Default,
                    )
                  )

                } yield assert(events0)(isNonEmpty) && assert(events1)(isNonEmpty) && assert(events2)(
                  isEmpty,
                ) && assert(
                  events1,
                )(hasSubset(events0))
              )

            } yield a && b && d
        }
      },
      test("should save and retrieve events correctly by props") {
        check(keyGen, versionGen, discriminatorGen) { (key, version, discriminator) =>
          for {
            persistence <- ZIO.service[CQRSPersistence]

            event = Change(version = version, DummyEvent("test"))

            saveResult <- (persistence.saveEvent(key, discriminator, event, Catalog.Default))

            a = assert(saveResult)(equalTo(1L))

            events0 <- (persistence.readEvents[DummyEvent](key, discriminator, Catalog.Default))
            events1 <- (
              persistence.readEvents[DummyEvent](
                discriminator,
                query = PersistenceQuery.props(EventProperty("type", "DummyEvent")),
                options = FetchOptions(),
                catalog = Catalog.Default,
              )
            )
            events2 <- (
              persistence.readEvents[DummyEvent](
                discriminator,
                query = PersistenceQuery.props(EventProperty("type", "DummyEventXXX")),
                options = FetchOptions(),
                catalog = Catalog.Default,
              )
            )
            b = assert(events0)(isNonEmpty) && assert(events1)(isNonEmpty) && assert(events2)(isEmpty)

          } yield a && b
        }
      },
      test("should save and retrieve events correctly") {
        check(keyGen, versionGen, discriminatorGen) { (key, version, discriminator) =>
          for {
            persistence <- ZIO.service[CQRSPersistence]

            event = Change(version = version, DummyEvent("test"))

            saveResult <- (persistence.saveEvent(key, discriminator, event, Catalog.Default))

            a = assert(saveResult)(equalTo(1L))

            events <- (persistence.readEvents[DummyEvent](key, discriminator, Catalog.Default))
            b = assert(events)(isNonEmpty) && assert(events)(equalTo(Chunk(event)))

          } yield a && b
        }
      },
      test("should save and retrieve an event correctly") {
        check(keyGen, versionGen, discriminatorGen) { (key, version, discriminator) =>
          for {
            persistence <- ZIO.service[CQRSPersistence]
            event = Change(version = version, DummyEvent("test"))

            saveResult <- (persistence.saveEvent(key, discriminator, event, Catalog.Default))
            result <- (persistence.readEvent[DummyEvent](version = event.version, Catalog.Default))
          } yield assert(result)(isSome(equalTo(event)))
        }
      },
      test("should save and retrieve events by namespace correctly") {
        check(keyGen, versionGen, discriminatorGen) { (key, version, discriminator) =>
          for {
            persistence <- ZIO.service[CQRSPersistence]

            event = Change(version = version, DummyEvent("test"))

            saveResult <- (persistence.saveEvent(key, discriminator, event, Catalog.Default))

            a = assert(saveResult)(equalTo(1L))

            events0 <- (
              persistence
                .readEvents[DummyEvent](
                  discriminator,
                  query = PersistenceQuery.ns(Namespace(0)),
                  options = FetchOptions(),
                  catalog = Catalog.Default,
                )
            )
            b = assert(events0)(isNonEmpty)

            events1 <- (
              persistence
                .readEvents[DummyEvent](
                  discriminator,
                  query = PersistenceQuery.ns(Namespace(1)),
                  options = FetchOptions(),
                  catalog = Catalog.Default,
                )
            )
            c = assert(events1)(isEmpty)

          } yield a && b
        }
      },
      test("should retrieve events in sorted order") {
        check(keyGen, Gen.listOfN(100)(versionGen), discriminatorGen) { (key, versions, discriminator) =>
          for {
            persistence <- ZIO.service[CQRSPersistence]

            events = versions.zipWithIndex.map { case (version, index) =>
              Change(version = version, payload = DummyEvent(s"${index + 1}"))
            }

            _ <-
              ZIO.foreachDiscard(events) { e =>
                persistence.saveEvent(key, discriminator, e, Catalog.Default)
              }

            es <- (persistence.readEvents[DummyEvent](key, discriminator, Catalog.Default))

            a = assert(es)(equalTo(es.sorted)) && assert(es)(equalTo(events.sorted))

          } yield a
        }
      },
      test("should retrieve events from given version") {
        check(keyGen, Gen.listOfN(100)(versionGen), Gen.listOfN(100)(versionGen), discriminatorGen) {
          (key, versions, moreVersions, discriminator) =>
            for {
              persistence <- ZIO.service[CQRSPersistence]

              events = versions.zipWithIndex.map { case (version, i) =>
                Change(version = version, payload = DummyEvent(s"${i + 1}"))
              }

              _ <-
                ZIO.foreachDiscard(events) { e =>
                  persistence.saveEvent(key, discriminator, e, Catalog.Default)
                }

              es <- (persistence.readEvents[DummyEvent](key, discriminator, Catalog.Default))

              a = assert((es))(equalTo((events.sorted)))

              max = es.maxBy(_.version)

              es1 <- (
                persistence.readEvents[DummyEvent](key, discriminator, snapshotVersion = max.version, Catalog.Default)
              )

              b = assert(es1)(isEmpty)

              events1 = moreVersions.zipWithIndex.map { case (version, i) =>
                Change(version = version, payload = DummyEvent(s"${i + 1}"))
              }

              _ <-
                ZIO.foreachDiscard(events1) { e =>
                  persistence.saveEvent(key, discriminator, e, Catalog.Default)
                }

              es2 <- (
                persistence.readEvents[DummyEvent](key, discriminator, snapshotVersion = max.version, Catalog.Default)
              )

              c = assert(es2)(equalTo(events1.sorted))

              max1 = es2.maxBy(_.version)
              es3 <- (
                persistence.readEvents[DummyEvent](key, discriminator, snapshotVersion = max1.version, Catalog.Default)
              )
              d = assert(es3)(isEmpty)
            } yield a && b && c && d
        }
      },
      test("should handle snapshot storage correctly") {
        check(keyGen, versionGen) { (key, version) =>
          for {
            persistence <- ZIO.service[CQRSPersistence]

            saveSnapshotResult <- (persistence.saveSnapshot(key, version))
            a = assert(saveSnapshotResult)(equalTo(1L))

            snapshot <- (persistence.readSnapshot(key))
            b = assert(snapshot)(isSome(equalTo(version)))

          } yield a && b
        }
      },
    ).provideSomeLayerShared(MemoryCQRSPersistence.live()) @@ TestAspect.withLiveClock

  val dummyEventGen: Gen[Any, DummyEvent] =
    Gen.alphaNumericStringBounded(4, 36).map(DummyEvent(_))

}

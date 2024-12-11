package com.youtoo
package cqrs
package service
package postgres

import com.youtoo.cqrs.config.*
import com.youtoo.cqrs.service.*

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.Codecs.given

import cats.implicits.*

import zio.*
import zio.prelude.*
import zio.test.*
import zio.test.Assertion.*
import zio.jdbc.*
import zio.schema.*

import zio.telemetry.opentelemetry.tracing.*

object PostgresCQRSPersistenceSpec extends PgSpec {
  case class DummyEvent(id: String)

  inline val grandParentIdXXX = 11L
  inline val parentIdXXX = 22L

  object DummyEvent {
    given Schema[DummyEvent] = DeriveSchema.gen[DummyEvent]

  }

  given MetaInfo[DummyEvent] {
    extension (self: DummyEvent) def namespace: Namespace = Namespace(0)
    extension (self: DummyEvent) def hierarchy: Option[Hierarchy] = Hierarchy.Descendant(Key(1L), Key(2L)).some
    extension (self: DummyEvent) def props: Chunk[EventProperty] = Chunk(EventProperty("type", "DummyEvent"))
    extension (self: DummyEvent) def reference: Option[Reference] = None

  }

  def spec =
    suite("PostgresCQRSPersistenceSpec")(
      test("should save and retrieve events correctly by hierarchy") {
        check(namespaceGen, keyGen, versionGen, keyGen, keyGen, discriminatorGen) {
          (ns, key, version, grandParentId, parentId, discriminator) =>

            given MetaInfo[DummyEvent] {
              extension (self: DummyEvent) def namespace: Namespace = ns
              extension (self: DummyEvent)
                def hierarchy: Option[Hierarchy] = Hierarchy.Descendant(grandParentId, parentId).some
              extension (self: DummyEvent) def props: Chunk[EventProperty] = Chunk(EventProperty("type", "DummyEvent"))
              extension (self: DummyEvent) def reference: Option[Reference] = None

            }

            for {
              persistence <- ZIO.service[CQRSPersistence]

              event = Change(version = version, DummyEvent("test"))

              saveResult <- atomically(persistence.saveEvent(key, discriminator, event))

              a = assert(saveResult)(equalTo(1L))

              events0 <- atomically(persistence.readEvents[DummyEvent](key, discriminator))
              b = assert(events0)(isNonEmpty)

              c <- (
                for {
                  events0 <- atomically(persistence.readEvents[DummyEvent](key, discriminator))
                  events1 <- atomically(
                    persistence.readEvents[DummyEvent](
                      discriminator,
                      query = PersistenceQuery.descendant(grandParentId, parentId),
                      options = FetchOptions(),
                    ),
                  )
                  events2 <- atomically(
                    persistence.readEvents[DummyEvent](
                      discriminator,
                      query = PersistenceQuery.descendant(Key(grandParentIdXXX), parentId),
                      options = FetchOptions(),
                    ),
                  )
                  events3 <- atomically(
                    persistence.readEvents[DummyEvent](
                      discriminator,
                      query = PersistenceQuery.descendant(grandParentId, Key(parentIdXXX)),
                      options = FetchOptions(),
                    ),
                  )

                } yield assert(events0)(isNonEmpty) && assert(events1)(isNonEmpty) && assert(events2)(
                  isEmpty,
                ) && assert(
                  events3,
                )(isEmpty) && assert(
                  events1,
                )(hasSubset(events0))
              )

              d <- (
                for {
                  events0 <- atomically(persistence.readEvents[DummyEvent](key, discriminator))
                  events1 <- atomically(
                    persistence.readEvents[DummyEvent](
                      discriminator,
                      query = PersistenceQuery.grandChild(grandParentId),
                      options = FetchOptions(),
                    ),
                  )
                  events2 <- atomically(
                    persistence.readEvents[DummyEvent](
                      discriminator,
                      query = PersistenceQuery.grandChild(Key(grandParentIdXXX)),
                      options = FetchOptions(),
                    ),
                  )

                } yield assert(events0)(isNonEmpty) && assert(events1)(isNonEmpty) && assert(events2)(
                  isEmpty,
                ) && assert(
                  events1,
                )(hasSubset(events0))
              )

              e <- (
                for {
                  events0 <- atomically(persistence.readEvents[DummyEvent](key, discriminator))
                  events1 <- atomically(
                    persistence.readEvents[DummyEvent](
                      discriminator,
                      query = PersistenceQuery.child(parentId),
                      options = FetchOptions(),
                    ),
                  )
                  events2 <- atomically(
                    persistence.readEvents[DummyEvent](
                      discriminator,
                      query = PersistenceQuery.child(Key(parentIdXXX)),
                      options = FetchOptions(),
                    ),
                  )

                } yield assert(events0)(isNonEmpty) && assert(events1)(isNonEmpty) && assert(events2)(
                  isEmpty,
                ) && assert(
                  events1,
                )(hasSubset(events0))
              )

            } yield a && b && c && d && e
        }
      },
      test("should save and retrieve events correctly by props") {
        check(keyGen, versionGen, discriminatorGen) { (key, version, discriminator) =>
          for {
            persistence <- ZIO.service[CQRSPersistence]

            event = Change(version = version, DummyEvent("test"))

            saveResult <- atomically(persistence.saveEvent(key, discriminator, event))

            a = assert(saveResult)(equalTo(1L))

            events0 <- atomically(persistence.readEvents[DummyEvent](key, discriminator))
            events1 <- atomically(
              persistence.readEvents[DummyEvent](
                discriminator,
                query = PersistenceQuery.props(EventProperty("type", "DummyEvent")),
                options = FetchOptions(),
              ),
            )
            events2 <- atomically(
              persistence.readEvents[DummyEvent](
                discriminator,
                query = PersistenceQuery.props(EventProperty("type", "DummyEventXXX")),
                options = FetchOptions(),
              ),
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

            saveResult <- atomically(persistence.saveEvent(key, discriminator, event))

            a = assert(saveResult)(equalTo(1L))

            events <- atomically(persistence.readEvents[DummyEvent](key, discriminator))
            b = assert(events)(isNonEmpty) && assert(events)(equalTo(Chunk(event)))

          } yield a && b
        }
      },
      test("should save and retrieve events by namespace correctly") {
        check(keyGen, versionGen, discriminatorGen) { (key, version, discriminator) =>
          for {
            persistence <- ZIO.service[CQRSPersistence]

            event = Change(version = version, DummyEvent("test"))

            saveResult <- atomically(persistence.saveEvent(key, discriminator, event))

            a = assert(saveResult)(equalTo(1L))

            events0 <- atomically(
              persistence
                .readEvents[DummyEvent](
                  discriminator,
                  query = PersistenceQuery.ns(Namespace(0)),
                  options = FetchOptions(),
                ),
            )
            b = assert(events0)(isNonEmpty)

            events1 <- atomically(
              persistence
                .readEvents[DummyEvent](
                  discriminator,
                  query = PersistenceQuery.ns(Namespace(1)),
                  options = FetchOptions(),
                ),
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

            _ <- atomically {
              ZIO.foreachDiscard(events) { e =>
                persistence.saveEvent(key, discriminator, e)
              }
            }

            es <- atomically(persistence.readEvents[DummyEvent](key, discriminator))

            a = assert(es)(isSorted) && assert(events)(isSorted) && assert(es)(equalTo(es.sorted)) && assert(es)(
              equalTo(events.sorted),
            )

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

              _ <- atomically {
                ZIO.foreachDiscard(events) { e =>
                  persistence.saveEvent(key, discriminator, e)
                }
              }

              es <- atomically(persistence.readEvents[DummyEvent](key, discriminator))

              a = assert((es))(equalTo((events.sorted)))

              max = es.maxBy(_.version)

              es1 <- atomically(
                persistence.readEvents[DummyEvent](key, discriminator, snapshotVersion = max.version),
              )

              b = assert(es1)(isEmpty)

              events1 = moreVersions.zipWithIndex.map { case (version, i) =>
                Change(version = version, payload = DummyEvent(s"${i + 1}"))
              }

              _ <- atomically {
                ZIO.foreachDiscard(events1) { e =>
                  persistence.saveEvent(key, discriminator, e)
                }
              }

              es2 <- atomically(
                persistence.readEvents[DummyEvent](key, discriminator, snapshotVersion = max.version),
              )

              c = assert(es2)(equalTo(events1.sorted))

              max1 = es2.maxBy(_.version)
              es3 <- atomically(
                persistence.readEvents[DummyEvent](key, discriminator, snapshotVersion = max1.version),
              )
              d = assert(es3)(isEmpty)
            } yield a && b && c && d
        }
      },
      test("should handle snapshot storage correctly") {
        check(keyGen, versionGen) { (key, version) =>
          for {
            persistence <- ZIO.service[CQRSPersistence]

            saveSnapshotResult <- atomically(persistence.saveSnapshot(key, version))
            a = assert(saveSnapshotResult)(equalTo(1L))

            snapshot <- atomically(persistence.readSnapshot(key))
            b = assert(snapshot)(isSome(equalTo(version)))

          } yield a && b
        }
      },
      test("read snapshot is optimized") {
        check(keyGen) { key =>

          val query = PostgresCQRSPersistence.Queries.READ_SNAPSHOT(key)
          for {

            executionTime <- atomically(query.selectOne).timed.map(_._1)
            timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

            executionPlan <- atomically(query.sql.getExecutionPlan)
            planAssertion = assert(executionPlan)(containsString("Index Scan") || containsString("Index Only Scan"))

          } yield planAssertion && timeAssertion
        }
      },
      test("read all events is optimized") {
        check(keyGen, discriminatorGen) { (key, discriminator) =>

          val query = PostgresCQRSPersistence.Queries.READ_EVENTS[DummyEvent](key, discriminator)
          for {

            executionTime <- atomically(query.selectAll).timed.map(_._1)
            timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

            executionPlan <- atomically(query.sql.getExecutionPlan)
            planAssertion = assert(executionPlan)(containsString("Index Scan") || containsString("Index Only Scan"))

          } yield planAssertion && timeAssertion
        }
      },
      test("read snapshot events is optimized") {
        check(keyGen, versionGen, discriminatorGen) { case (key, version, discriminator) =>
          val query = PostgresCQRSPersistence.Queries.READ_EVENTS[DummyEvent](key, discriminator, version)
          for {

            executionTime <- atomically(query.selectAll).timed.map(_._1)
            timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

            executionPlan <- atomically(query.sql.getExecutionPlan)
            planAssertion = assert(executionPlan)(containsString("Index Scan"))

          } yield planAssertion && timeAssertion
        }
      },
      test("read all events by args is optimized") {
        check(Gen.option(namespaceGen), Gen.option(hierarchyGen), Gen.option(eventPropertiesGen), discriminatorGen) {
          case (ns, hierarchy, props, discriminator) =>
            val query =
              PostgresCQRSPersistence.Queries
                .READ_EVENTS[DummyEvent](
                  discriminator,
                  query = PersistenceQuery.condition(namespace = ns, hierarchy = hierarchy, props = props),
                  options = FetchOptions(),
                )
            for {

              executionTime <- atomically(query.selectAll).timed.map(_._1)
              timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

              executionPlan <- atomically(query.sql.getExecutionPlan)
              planAssertion = assert(executionPlan)(containsString("Index Scan") || containsString("Index Only Scan"))

            } yield planAssertion && timeAssertion
        }
      },
      test("read snapshot events by args is optimized") {
        check(
          versionGen,
          Gen.option(namespaceGen),
          Gen.option(hierarchyGen),
          Gen.option(eventPropertiesGen),
          discriminatorGen,
        ) { case (version, ns, hierarchy, props, discriminator) =>
          val query =
            PostgresCQRSPersistence.Queries
              .READ_EVENTS[DummyEvent](
                discriminator,
                version,
                query = PersistenceQuery.condition(namespace = ns, hierarchy = hierarchy, props = props),
                options = FetchOptions(),
              )
          for {

            executionTime <- atomically(query.selectAll).timed.map(_._1)
            timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

            executionPlan <- atomically(query.sql.getExecutionPlan)
            planAssertion = assert(executionPlan)(containsString("Index Scan") || containsString("Index Only Scan"))

          } yield planAssertion && timeAssertion
        }
      },
    ).provideSomeLayerShared(
      ZLayer.make[Tracing & CQRSPersistence](
        tracingMockLayer(),
        zio.telemetry.opentelemetry.OpenTelemetry.contextZIO,
        PostgresCQRSPersistence.live(),
      ),
    ) @@ TestAspect.withLiveClock @@ TestAspect.beforeAll {
      for {
        config <- ZIO.service[DatabaseConfig]
        _ <- FlywayMigration.run(config)

      } yield ()

    }

  val namespaceGen: Gen[Any, Namespace] =
    Gen.int.map(Namespace(_))

  val discriminatorGen: Gen[Any, Discriminator] =
    Gen.alphaNumericStringBounded(5, 5).map(Discriminator(_))

  val keyGen: Gen[Any, Key] = Gen.fromZIO(Key.gen.orDie)
  val versionGen: Gen[Any, Version] = Gen.fromZIO(Version.gen.orDie)

  val hierarchyGen: Gen[Any, Hierarchy] = Gen.oneOf(
    keyGen map { parentId => Hierarchy.Child(parentId) },
    keyGen map { grandParentId => Hierarchy.GrandChild(grandParentId) },
    (keyGen <*> keyGen) map { case (grandParentId, parentId) => Hierarchy.Descendant(grandParentId, parentId) },
  )

  val namespacesGen: Gen[Any, NonEmptyList[Namespace]] =
    Gen
      .setOfBounded(1, 8)(Gen.int)
      .map(s =>
        NonEmptyList.fromIterableOption(s) match {
          case None => throw IllegalArgumentException("empty")
          case Some(nes) => nes.map(Namespace.apply)
        },
      )

  val eventPropertiesGen: Gen[Any, NonEmptyList[EventProperty]] =
    Gen
      .setOfBounded(1, 8)(
        (Gen.alphaNumericStringBounded(3, 20) <*> Gen.alphaNumericStringBounded(128, 512)) `map` EventProperty.apply,
      )
      .map(s => NonEmptyList.fromIterableOption(s).getOrElse(throw IllegalArgumentException("empty")))
}

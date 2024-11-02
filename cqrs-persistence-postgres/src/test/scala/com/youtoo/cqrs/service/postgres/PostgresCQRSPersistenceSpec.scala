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

object PostgresCQRSPersistenceSpec extends PgSpec {
  case class DummyEvent(id: String)

  object DummyEvent {
    given Schema[DummyEvent] = DeriveSchema.gen[DummyEvent]

    val discriminator = Discriminator("DummyEvent")
  }

  given MetaInfo[DummyEvent] with {
    extension (self: DummyEvent) def namespace: Namespace = Namespace(0)
    extension (self: DummyEvent)
      def hierarchy: Option[Hierarchy] = Hierarchy.Descendant(Key("grandParentId"), Key("parentId")).some
    extension (self: DummyEvent) def props: Chunk[EventProperty] = Chunk(EventProperty("type", "DummyEvent"))

  }

  def spec: Spec[ZConnectionPool & DatabaseConfig & FlywayMigration & TestEnvironment & Scope, Any] =
    suite("PostgresCQRSPersistenceSpec")(
      test("should save and retrieve events correctly by hierarchy") {
        for {
          persistence <- ZIO.service[CQRSPersistence]

          version <- Version.gen
          event = Change(version = version, DummyEvent("test"))
          key <- Key.gen

          saveResult <- atomically(persistence.saveEvent(key, DummyEvent.discriminator, event))

          a = assert(saveResult)(equalTo(1L))

          events0 <- atomically(persistence.readEvents[DummyEvent](key, DummyEvent.discriminator))
          b = assert(events0)(isNonEmpty)

          c <- (
            for {
              events1 <- atomically(
                persistence.readEvents[DummyEvent](
                  DummyEvent.discriminator,
                  ns = None,
                  hierarchy = Hierarchy.Descendant(Key("grandParentId"), Key("parentId")).some,
                  props = None,
                ),
              )
              events2 <- atomically(
                persistence.readEvents[DummyEvent](
                  DummyEvent.discriminator,
                  ns = None,
                  hierarchy = Hierarchy.Descendant(Key("grandParentIdXXX"), Key("parentId")).some,
                  props = None,
                ),
              )
              events3 <- atomically(
                persistence.readEvents[DummyEvent](
                  DummyEvent.discriminator,
                  ns = None,
                  hierarchy = Hierarchy.Descendant(Key("grandParentId"), Key("parentIdXXX")).some,
                  props = None,
                ),
              )

            } yield assert(events1)(isNonEmpty) && assert(events2)(isEmpty) && assert(events3)(isEmpty) && assert(
              events0,
            )(equalTo(events1))
          )

          d <- (
            for {
              events1 <- atomically(
                persistence.readEvents[DummyEvent](
                  DummyEvent.discriminator,
                  ns = None,
                  hierarchy = Hierarchy.GrandChild(Key("grandParentId")).some,
                  props = None,
                ),
              )
              events2 <- atomically(
                persistence.readEvents[DummyEvent](
                  DummyEvent.discriminator,
                  ns = None,
                  hierarchy = Hierarchy.GrandChild(Key("grandParentIdXXX")).some,
                  props = None,
                ),
              )
              events3 <- atomically(
                persistence.readEvents[DummyEvent](
                  DummyEvent.discriminator,
                  ns = None,
                  hierarchy = Hierarchy.GrandChild(Key("grandParentId")).some,
                  props = None,
                ),
              )

            } yield assert(events1)(isNonEmpty) && assert(events2)(isEmpty) && assert(events3)(isEmpty) && assert(
              events0,
            )(equalTo(events1))
          )

          e <- (
            for {
              events1 <- atomically(
                persistence.readEvents[DummyEvent](
                  DummyEvent.discriminator,
                  ns = None,
                  hierarchy = Hierarchy.Child(Key("parentId")).some,
                  props = None,
                ),
              )
              events2 <- atomically(
                persistence.readEvents[DummyEvent](
                  DummyEvent.discriminator,
                  ns = None,
                  hierarchy = Hierarchy.Child(Key("parentIdXXX")).some,
                  props = None,
                ),
              )

            } yield assert(events1)(isNonEmpty) && assert(events2)(isEmpty) && assert(events0)(equalTo(events1))
          )

        } yield a && b && c && d && e
      },
      test("should save and retrieve events correctly by props") {
        for {
          persistence <- ZIO.service[CQRSPersistence]

          version <- Version.gen
          event = Change(version = version, DummyEvent("test"))
          key <- Key.gen

          saveResult <- atomically(persistence.saveEvent(key, DummyEvent.discriminator, event))

          a = assert(saveResult)(equalTo(1L))

          events0 <- atomically(persistence.readEvents[DummyEvent](key, DummyEvent.discriminator))
          events1 <- atomically(
            persistence.readEvents[DummyEvent](
              DummyEvent.discriminator,
              ns = None,
              hierarchy = None,
              props = NonEmptyList(EventProperty("type", "DummyEvent")).some,
            ),
          )
          events2 <- atomically(
            persistence.readEvents[DummyEvent](
              DummyEvent.discriminator,
              ns = None,
              hierarchy = None,
              props = NonEmptyList(EventProperty("type", "DummyEventXXX")).some,
            ),
          )
          b = assert(events0)(isNonEmpty) && assert(events1)(isNonEmpty) && assert(events2)(isEmpty) && assert(events0)(
            equalTo(events1),
          )

        } yield a && b
      },
      test("should save and retrieve events correctly") {
        for {
          persistence <- ZIO.service[CQRSPersistence]

          version <- Version.gen
          event = Change(version = version, DummyEvent("test"))
          key <- Key.gen

          saveResult <- atomically(persistence.saveEvent(key, DummyEvent.discriminator, event))

          a = assert(saveResult)(equalTo(1L))

          events <- atomically(persistence.readEvents[DummyEvent](key, DummyEvent.discriminator))
          b = assert(events)(isNonEmpty)

        } yield a && b
      },
      test("should save and retrieve events by namespace correctly") {
        for {
          persistence <- ZIO.service[CQRSPersistence]

          version <- Version.gen
          event = Change(version = version, DummyEvent("test"))
          key <- Key.gen

          saveResult <- atomically(persistence.saveEvent(key, DummyEvent.discriminator, event))

          a = assert(saveResult)(equalTo(1L))

          events0 <- atomically(
            persistence
              .readEvents[DummyEvent](DummyEvent.discriminator, ns = NonEmptyList(Namespace(0)).some, None, None),
          )
          b = assert(events0)(isNonEmpty)

          events1 <- atomically(
            persistence
              .readEvents[DummyEvent](DummyEvent.discriminator, ns = NonEmptyList(Namespace(1)).some, None, None),
          )
          c = assert(events1)(isEmpty)

        } yield a && b
      },
      test("should retrieve events in sorted order") {
        for {
          persistence <- ZIO.service[CQRSPersistence]

          events <- ZIO.collectAll {
            (1 to 100).map { i =>
              for {
                version <- Version.gen
                ch = Change(version = version, payload = DummyEvent(s"$i"))

              } yield ch
            }

          }

          key <- Key.gen

          _ <- atomically {
            ZIO.foreachDiscard(events) { e =>
              persistence.saveEvent(key, DummyEvent.discriminator, e)
            }
          }

          es <- atomically(persistence.readEvents[DummyEvent](key, DummyEvent.discriminator))

          a = assert(es)(equalTo(es.sorted)) && assert(es)(equalTo(events.sorted))

        } yield a
      },
      test("should retrieve events from given version") {
        for {
          persistence <- ZIO.service[CQRSPersistence]

          events <- ZIO.collectAll {
            (1 to 100).map { i =>
              for {
                version <- Version.gen
                ch = Change(version = version, payload = DummyEvent(s"$i"))

              } yield ch
            }

          }

          key <- Key.gen

          _ <- atomically {
            ZIO.foreachDiscard(events) { e =>
              persistence.saveEvent(key, DummyEvent.discriminator, e)
            }
          }

          es <- atomically(persistence.readEvents[DummyEvent](key, DummyEvent.discriminator))

          a = assert((es))(equalTo((events.sorted)))

          max = es.maxBy(_.version)

          es1 <- atomically(
            persistence.readEvents[DummyEvent](key, DummyEvent.discriminator, snapshotVersion = max.version),
          )

          b = assert(es1)(isEmpty)

          events1 <- ZIO.collectAll {
            (1 to 100).map { i =>
              for {
                version <- Version.gen
                ch = Change(version = version, payload = DummyEvent(s"$i"))

              } yield ch
            }

          }

          _ <- atomically {
            ZIO.foreachDiscard(events1) { e =>
              persistence.saveEvent(key, DummyEvent.discriminator, e)
            }
          }

          es2 <- atomically(
            persistence.readEvents[DummyEvent](key, DummyEvent.discriminator, snapshotVersion = max.version),
          )

          c = assert(es2)(equalTo(events1.sorted))

          max1 = es2.maxBy(_.version)
          es3 <- atomically(
            persistence.readEvents[DummyEvent](key, DummyEvent.discriminator, snapshotVersion = max1.version),
          )
          d = assert(es3)(isEmpty)
        } yield a && b && c && d
      },
      test("should handle snapshot storage correctly") {
        for {
          persistence <- ZIO.service[CQRSPersistence]
          key <- Key.gen
          version <- Version.gen

          saveSnapshotResult <- atomically(persistence.saveSnapshot(key, version))
          a = assert(saveSnapshotResult)(equalTo(1L))

          snapshot <- atomically(persistence.readSnapshot(key))
          b = assert(snapshot)(isSome(equalTo(version)))

        } yield a && b
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
        check(keyGen) { key =>

          val query = PostgresCQRSPersistence.Queries.READ_EVENTS[DummyEvent](key, DummyEvent.discriminator)
          for {

            executionTime <- atomically(query.selectAll).timed.map(_._1)
            timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

            executionPlan <- atomically(query.sql.getExecutionPlan)
            planAssertion = assert(executionPlan)(containsString("Index Scan") || containsString("Index Only Scan"))

          } yield planAssertion && timeAssertion
        }
      },
      test("read snapshot events is optimized") {
        check(keyGen, versionGen) { case (key, version) =>
          val query = PostgresCQRSPersistence.Queries.READ_EVENTS[DummyEvent](key, DummyEvent.discriminator, version)
          for {

            executionTime <- atomically(query.selectAll).timed.map(_._1)
            timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

            executionPlan <- atomically(query.sql.getExecutionPlan)
            planAssertion = assert(executionPlan)(containsString("Index Scan"))

          } yield planAssertion && timeAssertion
        }
      },
      test("read all events by namespace is optimized") {
        check(Gen.option(namespacesGen), Gen.option(hierarchyGen), Gen.option(eventPropertiesGen)) {
          case (ns, hierarchy, props) =>
            val query =
              PostgresCQRSPersistence.Queries
                .READ_EVENTS[DummyEvent](DummyEvent.discriminator, ns, hierarchy, props)
            for {

              executionTime <- atomically(query.selectAll).timed.map(_._1)
              timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

              executionPlan <- atomically(query.sql.getExecutionPlan)
              planAssertion = assert(executionPlan)(containsString("Index Scan") || containsString("Index Only Scan"))

            } yield planAssertion && timeAssertion
        }
      },
      test("read snapshot events by namespace is optimized") {
        check(versionGen, Gen.option(namespacesGen), Gen.option(hierarchyGen), Gen.option(eventPropertiesGen)) {
          case (version, ns, hierarchy, props) =>
            val query =
              PostgresCQRSPersistence.Queries
                .READ_EVENTS[DummyEvent](DummyEvent.discriminator, version, ns, hierarchy, props)
            for {

              executionTime <- atomically(query.selectAll).timed.map(_._1)
              timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

              executionPlan <- atomically(query.sql.getExecutionPlan)
              planAssertion = assert(executionPlan)(containsString("Index Scan"))

            } yield planAssertion && timeAssertion
        }
      },
    ).provideSomeLayerShared(
      PostgresCQRSPersistence.live(),
    ) @@ TestAspect.sequential @@ TestAspect.withLiveClock @@ TestAspect.beforeAll {
      for {
        config <- ZIO.service[DatabaseConfig]
        _ <- FlywayMigration.run(config)

      } yield ()

    }

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

package com.github
package aris
package service
package postgres

import com.github.aris.postgres.*
import com.github.aris.postgres.config.*

import com.github.aris.service.*

import com.github.aris.domain.*

import com.github.aris.Codecs.given

import cats.implicits.*

import zio.*
import zio.prelude.*
import zio.test.*
import zio.test.Assertion.*
import zio.schema.*

object PostgresCQRSPersistenceSpec extends PgSpec, TestSupport {
  case class DummyEvent(id: String)

  inline val grandParentIdXXX = 11L
  inline val parentIdXXX = 22L

  object DummyEvent {
    given Schema[DummyEvent] = DeriveSchema.gen[DummyEvent]

  }

  given MetaInfo[DummyEvent] {
    extension (self: DummyEvent) def namespace: Namespace = Namespace(0)
    extension (self: DummyEvent) def hierarchy: Option[Hierarchy] = Hierarchy.Descendant(Key(1L), Key(2L)).some
    extension (self: DummyEvent) def props: Chunk[EventProperty] = Chunk(EventProperty("type", "DummyEvent"), EventProperty("id", 1L))
    extension (self: DummyEvent) def reference: Option[ReferenceKey] = None
    extension (self: DummyEvent) def timestamp: Option[Timestamp] = None

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
              extension (self: DummyEvent) def reference: Option[ReferenceKey] = None
              extension (self: DummyEvent) def timestamp: Option[Timestamp] = None

            }

            for {
              persistence <- ZIO.service[CQRSPersistence]
              tx <- ZIO.service[TransactionManager]

              event = Change(version = version, DummyEvent("test"))

              saveResult <- tx(persistence.saveEvent(key, discriminator, event, Catalog.Default))

              a = assert(saveResult)(equalTo(1L))

              events0 <- tx(persistence.readEvents[DummyEvent](key, discriminator, Catalog.Default))
              b = assert(events0)(isNonEmpty)

              c <- (
                for {
                  events0 <- tx(persistence.readEvents[DummyEvent](key, discriminator, Catalog.Default))
                  events1 <- tx(
                    persistence.readEvents[DummyEvent](
                      discriminator,
                      query = PersistenceQuery.descendant(grandParentId, parentId),
                      options = FetchOptions(),
                      catalog = Catalog.Default,
                    ),
                  )
                  events2 <- tx(
                    persistence.readEvents[DummyEvent](
                      discriminator,
                      query = PersistenceQuery.descendant(Key(grandParentIdXXX), parentId),
                      options = FetchOptions(),
                      catalog = Catalog.Default,
                    ),
                  )
                  events3 <- tx(
                    persistence.readEvents[DummyEvent](
                      discriminator,
                      query = PersistenceQuery.descendant(grandParentId, Key(parentIdXXX)),
                      options = FetchOptions(),
                      catalog = Catalog.Default,
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
                  events0 <- tx(persistence.readEvents[DummyEvent](key, discriminator, Catalog.Default))
                  events1 <- tx(
                    persistence.readEvents[DummyEvent](
                      discriminator,
                      query = PersistenceQuery.grandChild(grandParentId),
                      options = FetchOptions(),
                      catalog = Catalog.Default,
                    ),
                  )
                  events2 <- tx(
                    persistence.readEvents[DummyEvent](
                      discriminator,
                      query = PersistenceQuery.grandChild(Key(grandParentIdXXX)),
                      options = FetchOptions(),
                      catalog = Catalog.Default,
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
                  events0 <- tx(persistence.readEvents[DummyEvent](key, discriminator, Catalog.Default))
                  events1 <- tx(
                    persistence.readEvents[DummyEvent](
                      discriminator,
                      query = PersistenceQuery.child(parentId),
                      options = FetchOptions(),
                      catalog = Catalog.Default,
                    ),
                  )
                  events2 <- tx(
                    persistence.readEvents[DummyEvent](
                      discriminator,
                      query = PersistenceQuery.child(Key(parentIdXXX)),
                      options = FetchOptions(),
                      catalog = Catalog.Default,
                    ),
                  )

                } yield assert(events0)(isNonEmpty) && assert(events1)(isNonEmpty) && assert(events2)(
                  isEmpty,
                ) && assert(
                  events1,
                )(hasSubset(events0))
              )

              f <- (
                for {
                  events0 <- tx(persistence.readEvents[DummyEvent](key, discriminator, Catalog.Default))
                  events1 <- tx(
                    persistence.readEvents[DummyEvent](
                      id = key,
                      discriminator,
                      query = PersistenceQuery.descendant(grandParentId, parentId),
                      options = FetchOptions(),
                      catalog = Catalog.Default,
                    ),
                  )
                  events2 <- tx(
                    persistence.readEvents[DummyEvent](
                      id = key,
                      discriminator,
                      query = PersistenceQuery.descendant(Key(grandParentIdXXX), parentId),
                      options = FetchOptions(),
                      catalog = Catalog.Default,
                    ),
                  )
                  events3 <- tx(
                    persistence.readEvents[DummyEvent](
                      id = key,
                      discriminator,
                      query = PersistenceQuery.descendant(grandParentId, Key(parentIdXXX)),
                      options = FetchOptions(),
                      catalog = Catalog.Default,
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

            } yield a && b && c && d && e && f
        }
      }.provideSomeLayer(PostgresCQRSPersistence.live()),
      test("should save and retrieve an event correctly") {
        check(keyGen, versionGen, discriminatorGen) { (key, version, discriminator) =>
          for {
            persistence <- ZIO.service[CQRSPersistence]
              tx <- ZIO.service[TransactionManager]
            event = Change(version = version, DummyEvent("test"))

            saveResult <- tx(persistence.saveEvent(key, discriminator, event, Catalog.Default))
            result <- tx(persistence.readEvent[DummyEvent](version = event.version, Catalog.Default))
          } yield assert(result)(isSome(equalTo(event)))
        }
      }.provideSomeLayer(PostgresCQRSPersistence.live()),
      test("should save and retrieve events correctly by props") {
        check(keyGen, versionGen, discriminatorGen) { (key, version, discriminator) =>
          for {
            persistence <- ZIO.service[CQRSPersistence]
              tx <- ZIO.service[TransactionManager]

            event = Change(version = version, DummyEvent("test"))

            saveResult <- tx(persistence.saveEvent(key, discriminator, event, Catalog.Default))

            a = assert(saveResult)(equalTo(1L))

            events0 <- tx(persistence.readEvents[DummyEvent](key, discriminator, Catalog.Default))
            events1 <- tx(
              persistence.readEvents[DummyEvent](
                discriminator,
                query = PersistenceQuery.props(EventProperty("type", "DummyEvent")),
                options = FetchOptions(),
                catalog = Catalog.Default,
              ),
            )
            events2 <- tx(
              persistence.readEvents[DummyEvent](
                discriminator,
                query = PersistenceQuery.props(EventProperty("type", "DummyEventXXX")),
                options = FetchOptions(),
                catalog = Catalog.Default,
              ),
            )
            b = assert(events0)(isNonEmpty) && assert(events1)(isNonEmpty) && assert(events2)(isEmpty)

          } yield a && b
        }
      }.provideSomeLayer(PostgresCQRSPersistence.live()),
      test("should save and retrieve events correctly") {
        check(keyGen, versionGen, discriminatorGen) { (key, version, discriminator) =>
          for {
            persistence <- ZIO.service[CQRSPersistence]
              tx <- ZIO.service[TransactionManager]

            event = Change(version = version, DummyEvent("test"))

            saveResult <- tx(persistence.saveEvent(key, discriminator, event, Catalog.Default))

            a = assert(saveResult)(equalTo(1L))

            events <- tx(persistence.readEvents[DummyEvent](key, discriminator, Catalog.Default))
            b = assert(events)(isNonEmpty) && assert(events)(equalTo(Chunk(event)))

          } yield a && b
        }
      }.provideSomeLayer(PostgresCQRSPersistence.live()),
      test("should save and retrieve events by namespace correctly") {
        check(keyGen, versionGen, discriminatorGen) { (key, version, discriminator) =>
          for {
            persistence <- ZIO.service[CQRSPersistence]
              tx <- ZIO.service[TransactionManager]

            event = Change(version = version, DummyEvent("test"))

            saveResult <- tx(persistence.saveEvent(key, discriminator, event, Catalog.Default))

            a = assert(saveResult)(equalTo(1L))

            events0 <- tx(
              persistence
                .readEvents[DummyEvent](
                  discriminator,
                  query = PersistenceQuery.ns(Namespace(0)),
                  options = FetchOptions(),
                  catalog = Catalog.Default,
                ),
            )
            b = assert(events0)(isNonEmpty)

            events1 <- tx(
              persistence
                .readEvents[DummyEvent](
                  discriminator,
                  query = PersistenceQuery.ns(Namespace(1)),
                  options = FetchOptions(),
                  catalog = Catalog.Default,
                ),
            )
            c = assert(events1)(isEmpty)

          } yield a && b
        }
      }.provideSomeLayer(PostgresCQRSPersistence.live()),
      test("should retrieve events in sorted order") {
        check(keyGen, Gen.listOfN(100)(versionGen), discriminatorGen) { (key, versions, discriminator) =>
          for {
            persistence <- ZIO.service[CQRSPersistence]
              tx <- ZIO.service[TransactionManager]

            events = versions.zipWithIndex.map { case (version, index) =>
              Change(version = version, payload = DummyEvent(s"${index + 1}"))
            }

            _ <- tx {
              ZIO.foreachDiscard(events) { e =>
                persistence.saveEvent(key, discriminator, e, Catalog.Default)
              }
            }

            es <- tx(persistence.readEvents[DummyEvent](key, discriminator, Catalog.Default))

            a = assert(es)(isSorted) && assert(events)(isSorted) && assert(es)(equalTo(es.sorted)) && assert(es)(
              equalTo(events.sorted),
            )

          } yield a
        }
      }.provideSomeLayer(PostgresCQRSPersistence.live()),
      test("should retrieve events with fetch options") {
        check(keyGen, Gen.setOfN(100)(Gen.long), discriminatorGen, Gen.long(0, 100)) { (key, indices, discriminator, l) =>
          for {
            persistence <- ZIO.service[CQRSPersistence]
              tx <- ZIO.service[TransactionManager]

            _ <- tx {
              ZIO.foreachDiscard(indices) { index =>
                for {
                  version <- Version.gen
                  ch = Change(version = version, payload = DummyEvent(s"${index + 1}"))

                  _ <- persistence.saveEvent(key, discriminator, ch, Catalog.Default)
                } yield ()
              }
            }

            es <- tx(persistence.readEvents[DummyEvent](
              discriminator,
              query = PersistenceQuery.condition(),
              FetchOptions(None, Some(l), FetchOptions.Order.asc),
              catalog = Catalog.Default,
            ))

            a = assert(es.size)(equalTo(l))

          } yield a
        }
      }.provideSomeLayer(PostgresCQRSPersistence.live()),
      test("should retrieve events with query and fetch option") {
        check(
          discriminatorGen,
          persistenceQueryGen,
          fetchOptionsGen,
        ) { (discriminator, query, options) =>
          for {
            persistence <- ZIO.service[CQRSPersistence]
              tx <- ZIO.service[TransactionManager]

            _ <- tx(persistence.readEvents[DummyEvent](
              discriminator,
              query = query,
              options = options,
              catalog = Catalog.Default,
            ))

          } yield assertCompletes
        }
      }.provideSomeLayer(PostgresCQRSPersistence.live()),
      test("should retrieve events with query and fetch option for aggregate") {
        check(
          keyGen,
          discriminatorGen,
          persistenceQueryGen,
          fetchOptionsGen,
        ) { (id, discriminator, query, options) =>
          for {
            persistence <- ZIO.service[CQRSPersistence]
              tx <- ZIO.service[TransactionManager]

            _ <- tx(persistence.readEvents[DummyEvent](
              id = id,
              discriminator = discriminator,
              query = query,
              options = options,
              catalog = Catalog.Default,
            ))

          } yield assertCompletes
        }
      }.provideSomeLayer(PostgresCQRSPersistence.live()),
      test("should retrieve events from given version") {
        check(keyGen, Gen.listOfN(100)(versionGen), Gen.listOfN(100)(versionGen), discriminatorGen) {
          (key, versions, moreVersions, discriminator) =>
            for {
              persistence <- ZIO.service[CQRSPersistence]
              tx <- ZIO.service[TransactionManager]

              events = versions.zipWithIndex.map { case (version, i) =>
                Change(version = version, payload = DummyEvent(s"${i + 1}"))
              }

              _ <- tx {
                ZIO.foreachDiscard(events) { e =>
                  persistence.saveEvent(key, discriminator, e, Catalog.Default)
                }
              }

              es <- tx(persistence.readEvents[DummyEvent](key, discriminator, Catalog.Default))

              a = assert((es))(equalTo((events.sorted)))

              max = es.maxBy(_.version)

              es1 <- tx(
                persistence.readEvents[DummyEvent](key, discriminator, snapshotVersion = max.version, Catalog.Default),
              )

              b = assert(es1)(isEmpty)

              events1 = moreVersions.zipWithIndex.map { case (version, i) =>
                Change(version = version, payload = DummyEvent(s"${i + 1}"))
              }

              _ <- tx {
                ZIO.foreachDiscard(events1) { e =>
                  persistence.saveEvent(key, discriminator, e, Catalog.Default)
                }
              }

              es2 <- tx(
                persistence.readEvents[DummyEvent](key, discriminator, snapshotVersion = max.version, Catalog.Default),
              )

              c = assert(es2)(equalTo(events1.sorted))

              max1 = es2.maxBy(_.version)
              es3 <- tx(
                persistence.readEvents[DummyEvent](key, discriminator, snapshotVersion = max1.version, Catalog.Default),
              )
              d = assert(es3)(isEmpty)
            } yield a && b && c && d
        }
      }.provideSomeLayer(PostgresCQRSPersistence.live()),
      test("should handle snapshot storage correctly") {
        check(keyGen, versionGen) { (key, version) =>
          for {
            persistence <- ZIO.service[CQRSPersistence]
            tx <- ZIO.service[TransactionManager]

            saveSnapshotResult <- tx(persistence.saveSnapshot(key, version))
            a = assert(saveSnapshotResult)(equalTo(1L))

            snapshot <- tx(persistence.readSnapshot(key))
            b = assert(snapshot)(isSome(equalTo(version)))

          } yield a && b
        }
      }.provideSomeLayer(PostgresCQRSPersistence.live()),
      test("read snapshot is optimized") {
        check(keyGen) { key =>

          val query = PostgresCQRSPersistence.Queries.READ_SNAPSHOT(key)
          for {
            tx <- ZIO.service[TransactionManager]

            executionTime <- tx(query.selectOne).timed.map(_._1)
            timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

            executionPlan <- tx(query.sql.getExecutionPlan)
            planAssertion = assert(executionPlan)(containsString("Index Scan") || containsString("Index Only Scan"))

          } yield planAssertion && timeAssertion
        }
      }.provideSomeLayer(PostgresCQRSPersistence.live()),
      test("read an event is optimized") {
        check(versionGen, discriminatorGen) { (version, discriminator) =>

          val query = PostgresCQRSPersistence.Queries.READ_EVENT[DummyEvent](version, Catalog.Default)
          for {
            tx <- ZIO.service[TransactionManager]

            executionTime <- tx(query.selectOne).timed.map(_._1)
            timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

            executionPlan <- tx(query.sql.getExecutionPlan)
            planAssertion = assert(executionPlan)(containsString("Index Scan") || containsString("Index Only Scan"))

          } yield planAssertion && timeAssertion
        }
      }.provideSomeLayer(PostgresCQRSPersistence.live()),
      test("read all events is optimized") {
        check(keyGen, discriminatorGen) { (key, discriminator) =>

          val query = PostgresCQRSPersistence.Queries.READ_EVENTS[DummyEvent](key, discriminator, Catalog.Default)
          for {
            tx <- ZIO.service[TransactionManager]

            executionTime <- tx(query.selectAll).timed.map(_._1)
            timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

            executionPlan <- tx(query.sql.getExecutionPlan)
            planAssertion = assert(executionPlan)(containsString("Index Scan") || containsString("Index Only Scan"))

          } yield planAssertion && timeAssertion
        }
      }.provideSomeLayer(PostgresCQRSPersistence.live()),
      test("read snapshot events is optimized") {
        check(keyGen, versionGen, discriminatorGen) { case (key, version, discriminator) =>
          val query = PostgresCQRSPersistence.Queries.READ_EVENTS[DummyEvent](key, discriminator, version, Catalog.Default)
          for {
            tx <- ZIO.service[TransactionManager]

            executionTime <- tx(query.selectAll).timed.map(_._1)
            timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

            executionPlan <- tx(query.sql.getExecutionPlan)
            planAssertion = assert(executionPlan)(containsString("Index Scan"))

          } yield planAssertion && timeAssertion
        }
      }.provideSomeLayer(PostgresCQRSPersistence.live()),
      test("read all events by args is optimized") {
        check(
          persistenceQueryGen,
          fetchOptionsGen,
          discriminatorGen
        ) {
          case (q, options, discriminator) =>
            val query =
              PostgresCQRSPersistence.Queries
                .READ_EVENTS[DummyEvent](
                  discriminator,
                  query = q,
                  options = options,
                  catalog = Catalog.Default,
                )
            for {
              tx <- ZIO.service[TransactionManager]

              executionTime <- tx(query.selectAll).timed.map(_._1)
              timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

              executionPlan <- tx(query.sql.getExecutionPlan)
              planAssertion = assert(executionPlan)(containsString("Index Scan") || containsString("Index Only Scan"))

            } yield planAssertion && timeAssertion
        }
      }.provideSomeLayer(PostgresCQRSPersistence.live()),
      test("read all events by time interval is optimized") {
        check(
          persistenceQueryGen,
          timeIntervalGen,
          discriminatorGen
        ) {
          case (q, timeInterval, discriminator) =>
            val query =
              PostgresCQRSPersistence.Queries
                .READ_EVENTS[DummyEvent](
                  discriminator,
                  query = q,
                  timeInterval,
                  catalog = Catalog.Default,
                )
            for {
              tx <- ZIO.service[TransactionManager]

              executionTime <- tx(query.selectAll).timed.map(_._1)
              timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

              executionPlan <- tx(query.sql.getExecutionPlan)
              planAssertion = assert(executionPlan)(containsString("Index Scan") || containsString("Index Only Scan"))

            } yield planAssertion && timeAssertion
        }
      }.provideSomeLayer(PostgresCQRSPersistence.live()),
      test("read all events by args for aggregate is optimized") {
        check(
          keyGen,
          persistenceQueryGen,
          fetchOptionsGen,
          discriminatorGen
        ) {
          case (id, q, options, discriminator) =>
            val query =
              PostgresCQRSPersistence.Queries
                .READ_EVENTS[DummyEvent](
                  id,
                  discriminator,
                  query = q,
                  options = options,
                  catalog = Catalog.Default,
                )
            for {
              tx <- ZIO.service[TransactionManager]

              executionTime <- tx(query.selectAll).timed.map(_._1)
              timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

              executionPlan <- tx(query.sql.getExecutionPlan)
              planAssertion = assert(executionPlan)(containsString("Index Scan") || containsString("Index Only Scan"))

            } yield planAssertion && timeAssertion
        }
      }.provideSomeLayer(PostgresCQRSPersistence.live()),
      test("read all events by args for time interval aggregate is optimized") {
        check(
          keyGen,
          persistenceQueryGen,
          timeIntervalGen,
          discriminatorGen
        ) {
          case (id, q, timeInterval, discriminator) =>
            val query =
              PostgresCQRSPersistence.Queries
                .READ_EVENTS[DummyEvent](
                  id,
                  discriminator,
                  query = q,
                  timeInterval,
                  catalog = Catalog.Default,
                )
            for {
              tx <- ZIO.service[TransactionManager]

              executionTime <- tx(query.selectAll).timed.map(_._1)
              timeAssertion = assert(executionTime.toMillis)(isLessThanEqualTo(100L))

              executionPlan <- tx(query.sql.getExecutionPlan)
              planAssertion = assert(executionPlan)(containsString("Index Scan") || containsString("Index Only Scan"))

            } yield planAssertion && timeAssertion
        }
      }.provideSomeLayer(PostgresCQRSPersistence.live()),
    ) @@ TestAspect.withLiveClock @@ TestAspect.nondeterministic @@ TestAspect.beforeAll {
      for {
        config <- ZIO.service[DatabaseConfig]
        _ <- FlywayMigration.run(config)

      } yield ()

    }

}


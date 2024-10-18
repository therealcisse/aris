package com.youtoo.cqrs
package service
package postgres

import com.youtoo.cqrs.config.*
import com.youtoo.cqrs.service.*

import com.youtoo.cqrs.domain.*
import com.youtoo.cqrs.Codecs.given

import cats.implicits.*

import zio.*
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

  def spec: Spec[ZConnectionPool & DatabaseConfig & Migration & TestEnvironment & Scope, Any] =
    suite("PostgresCQRSPersistenceSpec")(
      test("should save and retrieve events correctly") {
        for {
          persistence <- ZIO.service[CQRSPersistence]

          version <- Version.gen
          event = Change(version = version, DummyEvent("test"))
          key <- Key.gen

          saveResult <- atomically(persistence.saveEvent(key, DummyEvent.discriminator, event))

          a <- assert(saveResult)(equalTo(1L))

          events <- atomically(persistence.readEvents[DummyEvent](key, DummyEvent.discriminator))
          b <- assert(events)(isNonEmpty)

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

          a <- assert(es)(equalTo(es.sortBy(_.version)))

        } yield a
      },
      test("should retrieve events from given version") {
        for {
          persistence <- ZIO.service[CQRSPersistence]

          events <- ZIO.collectAll {
            (0 to 100).map { i =>
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

          a <- assert(es)(equalTo(events))

          max = es.maxBy(_.version)

          es1 <- atomically(
            persistence.readEvents[DummyEvent](key, DummyEvent.discriminator, snapshotVersion = max.version),
          )

          b <- assert(es1)(isEmpty)

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

          c <- assert(es2)(equalTo(events1))

          max1 = es2.maxBy(_.version)
          es3 <- atomically(
            persistence.readEvents[DummyEvent](key, DummyEvent.discriminator, snapshotVersion = max1.version),
          )
          d <- assert(es3)(isEmpty)
        } yield a && b && c && d
      },
      test("should handle snapshot storage correctly") {
        for {
          persistence <- ZIO.service[CQRSPersistence]
          key <- Key.gen
          version <- Version.gen

          saveSnapshotResult <- atomically(persistence.saveSnapshot(key, version))
          a <- assert(saveSnapshotResult)(equalTo(1L))

          snapshot <- atomically(persistence.readSnapshot(key))
          b <- assert(snapshot)(isSome(equalTo(version)))

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
    ).provideSomeLayerShared(
      PostgresCQRSPersistence.live(),
    ) @@ TestAspect.sequential @@ TestAspect.withLiveClock @@ TestAspect.beforeAll {
      for {
        config <- ZIO.service[DatabaseConfig]
        _ <- Migration.run(config)

      } yield ()

    }

  val keyGen: Gen[Any, Key] = Gen.fromZIO(Key.gen.orDie)
  val versionGen: Gen[Any, Version] = Gen.fromZIO(Version.gen.orDie)

}

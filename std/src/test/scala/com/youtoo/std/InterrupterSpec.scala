package com.youtoo
package std

import zio.test.*
import zio.test.Assertion.*
import zio.*

object InterrupterSpec extends ZIOSpecDefault {
  def spec = suite("InterrupterSpec")(
    test("watch returns a new promise if not existing") {
      for {
        ref <- Ref.Synchronized.make(Map.empty[Key, Promise[Throwable, Unit]])
        interrupter = new Interrupter.Live(ref)
        key = Key("key1")
        promises0 <- interrupter.watch(key)(_ => ref.get)
        promises <- ref.get
      } yield assert(promises0.contains(key))(isTrue) && assert(promises.contains(key))(isFalse) &&
        assert(promises.get(key))(isNone)
    },
    test("watch returns existing promise if already present") {
      for {
        ref <- Ref.Synchronized.make(Map.empty[Key, Promise[Throwable, Unit]])
        interrupter = new Interrupter.Live(ref)
        key = Key("key1")
        res <- interrupter.watch(key)(p => interrupter.watch(key)(g => ZIO.succeed((p, g))))
      } yield assert(res._1)(equalTo(res._2))
    },
    test("interrupt completes the promise") {
      for {
        ref <- Ref.Synchronized.make(Map.empty[Key, Promise[Throwable, Unit]])
        interrupter = new Interrupter.Live(ref)
        key = Key("key1")
        fiber <- interrupter.watch(key)(promise => promise.await).fork
        _ <- interrupter.interrupt(key)
        result <- fiber.join
      } yield assert(result)(isUnit)
    },
  )
}

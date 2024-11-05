package com.youtoo
package std

import zio.test.*
import zio.test.Assertion.*
import zio.*
import java.util.concurrent.TimeUnit

object InterrupterSpec extends ZIOSpecDefault {
  def spec = suite("InterrupterSpec")(
    test("watch returns a new promise if not existing") {
      for {
        ref <- Ref.Synchronized.make(Map.empty[Key, Promise[Throwable, Unit]])
        interrupter = new Interrupter.Live(ref)
        key = Key(1L)
        promises0 <- interrupter.watch(key)(_ => ref.get)
        promises <- ref.get
      } yield assert(promises0.contains(key))(isTrue) && assert(promises.contains(key))(isFalse)
    },
    test("watch returns existing promise if already present") {
      for {
        ref <- Ref.Synchronized.make(Map.empty[Key, Promise[Throwable, Unit]])
        interrupter = new Interrupter.Live(ref)
        key = Key(1L)
        res <- interrupter.watch(key)(p => interrupter.watch(key)(g => ZIO.succeed((p, g))))
      } yield assert(res._1)(equalTo(res._2))
    },
    test("interrupt completes the promise") {
      for {
        ref <- Ref.Synchronized.make(Map.empty[Key, Promise[Throwable, Unit]])
        interrupter = new Interrupter.Live(ref)
        key = Key(1L)
        fiber <- interrupter.watch(key)(promise => promise.await).fork
        _ <- interrupter.interrupt(key)
        result <- fiber.join
      } yield assert(result)(isUnit)
    },
  ) @@ TestAspect.timeout(Duration(30L, TimeUnit.SECONDS))
}

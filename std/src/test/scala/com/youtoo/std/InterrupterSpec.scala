package com.youtoo
package std

import zio.test.*
import zio.test.Assertion.*
import zio.*
import java.util.concurrent.TimeUnit

object InterrupterSpec extends ZIOSpecDefault {
  def spec = suite("InterrupterSpec")(
    test("watch returns a new promise if not existing") {
      checkPar(Gen.long, sys.runtime.availableProcessors) { id =>
        for {
          ref <- Ref.Synchronized.make(Map.empty[Key, Promise[Throwable, Unit]])
          interrupter = new Interrupter.Live(ref)
          key = Key(id)
          promises0 <- ZIO.scoped(interrupter.watch(key) *> ref.get)
          promises <- ref.get
        } yield assert(promises0.contains(key))(isTrue) && assert(promises.contains(key))(isFalse)
      }
    },
    test("watch returns existing promise if already present") {
      checkPar(Gen.long, sys.runtime.availableProcessors) { id =>
        for {
          ref <- Ref.Synchronized.make(Map.empty[Key, Promise[Throwable, Unit]])
          interrupter = new Interrupter.Live(ref)
          key = Key(id)
          res <- ZIO.scoped((interrupter.watch(key) <&> interrupter.watch(key)))
        } yield assert(res._1)(equalTo(res._2))
      }
    },
    test("interrupt completes the promise") {
      checkPar(Gen.long, sys.runtime.availableProcessors) { id =>
        for {
          ref <- Ref.Synchronized.make(Map.empty[Key, Promise[Throwable, Unit]])
          interrupter = new Interrupter.Live(ref)
          key = Key(id)
          added <- Promise.make[Nothing, Unit]
          fiber <- (interrupter.watch(key).flatMap(promise => added.succeed(()) *> promise.await)).fork
          _ <- added.await
          _ <- interrupter.interrupt(key)
          result <- fiber.join
        } yield assert(result)(isUnit)
      }
    },
  ) @@ TestAspect.withLiveClock @@ TestAspect.timeout(Duration(30L, TimeUnit.SECONDS))
}

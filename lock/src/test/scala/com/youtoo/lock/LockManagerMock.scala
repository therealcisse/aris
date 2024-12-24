package com.youtoo
package lock

import zio.*
import zio.mock.*

import zio.telemetry.opentelemetry.tracing.Tracing

object LockManagerMock extends Mock[LockManager] {

  object AcquireScoped extends Effect[Lock, Throwable, Boolean]
  object Locks extends Effect[Unit, Throwable, Chunk[Lock.Info]]

  val compose: URLayer[Proxy, LockManager] =
    ZLayer.fromFunction { (proxy: Proxy) =>
      new LockManager {
        def aquireScoped(lock: Lock): ZIO[Scope & Tracing, Throwable, Boolean] =
          proxy(AcquireScoped, lock)

        def locks: Task[Chunk[Lock.Info]] =
          proxy(Locks)
      }
    }
}

package com.youtoo
package lock
package repository

import zio.*
import zio.mock.*
import zio.jdbc.*

object LockRepositoryMock extends Mock[LockRepository] {

  // Define mock effects for LockRepository methods
  object Acquire extends Effect[Lock, Throwable, Boolean]
  object Release extends Effect[Lock, Throwable, Boolean]
  object Locks extends Effect[Unit, Throwable, Chunk[Lock]]

  // Construct the mock layer using ZLayer.fromFunction
  val compose: URLayer[Proxy, LockRepository] =
    ZLayer.fromFunction { (proxy: Proxy) =>
      new LockRepository {
        def acquire(lock: Lock): RIO[ZConnection, Boolean] =
          proxy(Acquire, lock)

        def release(lock: Lock): RIO[ZConnection, Boolean] =
          proxy(Release, lock)

        def locks: RIO[ZConnection, Chunk[Lock]] =
          proxy(Locks)
      }
    }
}

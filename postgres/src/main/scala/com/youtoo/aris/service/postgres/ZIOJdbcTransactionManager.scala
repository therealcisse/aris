package com.youtoo
package aris
package service
package postgres

import zio.*
import zio.jdbc.*

object ZIOJdbcTransactionManager {
  def live(): ZLayer[ZConnectionPool, Throwable, TransactionManager[ZConnection]] =
    ZLayer.fromFunction { (pool: ZConnectionPool) =>
      new ZIOJdbcTransactionManagerLive(pool)

    }

  class ZIOJdbcTransactionManagerLive(pool: ZConnectionPool) extends TransactionManager[ZConnection] {

    def apply[R, T](fa: ZIO[R & ZConnection, Throwable, T]): RIO[R, T] =
      val layer = ZLayer.makeSome[R & ZConnectionPool, R & ZConnection](transaction)

      fa.provideLayer(ZLayer.succeed(pool) >>> layer)

  }

}

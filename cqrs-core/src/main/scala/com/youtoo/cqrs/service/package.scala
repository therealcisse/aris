package com.youtoo
package cqrs
package service

import zio.*
import zio.jdbc.*

inline def atomically[R, T](fa: ZIO[R & ZConnection, Throwable, T]): RIO[R & ZConnectionPool, T] =
  val layer = ZLayer.makeSome[R & ZConnectionPool, R & ZConnection](transaction)

  fa.provideLayer(layer)

package com.youtoo
package cqrs
package service

import zio.*
import zio.jdbc.*

extension [R, T](fa: ZIO[R & ZConnection, Throwable, T])
  inline def atomically: RIO[R & ZConnectionPool, T] =
    val layer = ZLayer.makeSome[R & ZConnectionPool, R & ZConnection](transaction)

    fa.provideLayer(layer)

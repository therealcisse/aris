package com.youtoo.cqrs
package service
package postgres

import zio.*
import zio.jdbc.*

import org.mockito.Mockito

object ZConnectionMock {

  def pool(): ULayer[ZConnectionPool] =
    ZLayer.succeed {
      val conn = Mockito.mock(classOf[ZConnection])

      new ZConnectionPool {
        def transaction = ZLayer.succeed(conn)
        def invalidate(conn: ZConnection): UIO[Any] = ???

      }
    }

}

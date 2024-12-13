package com.youtoo
package postgres

import zio.*
import zio.jdbc.*

import org.mockito.Mockito

object ZConnectionMock {

  def pool(): ULayer[ZConnectionPool] =
    ZLayer.succeed {

      new ZConnectionPool {
        def transaction = ZLayer.succeed {
          val conn: ZConnection = Mockito.mock(classOf[ZConnection])
          conn
        }

        def invalidate(conn: ZConnection): UIO[Any] = ???

      }
    }

}

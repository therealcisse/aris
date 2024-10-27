package com.youtoo
package std

import zio.*
import zio.mock.*

object HealthcheckMock extends Mock[Healthcheck] {

  object Start extends Effect[(Key, Schedule[Any, Any, Any]), Throwable, Healthcheck.Handle]
  object GetHeartbeat extends Effect[Key, Nothing, Option[Timestamp]]
  object IsRunning extends Effect[Key, Nothing, Boolean]

  val compose: URLayer[Proxy, Healthcheck] = ZLayer.fromFunction { (proxy: Proxy) =>
    new Healthcheck {
      def start(id: Key, interval: Schedule[Any, Any, Any]): Task[Healthcheck.Handle] =
        proxy(Start, (id, interval))

      def getHeartbeat(id: Key): UIO[Option[Timestamp]] =
        proxy(GetHeartbeat, id)

      def isRunning(id: Key): UIO[Boolean] =
        proxy(IsRunning, id)
    }
  }
}

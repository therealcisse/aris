package com.youtoo
package sink

import com.youtoo.cqrs.*
import com.youtoo.sink.model.*
import zio.*
import zio.mock.*

object MockSinkCQRS extends Mock[SinkCQRS] {

  // Define each action as an effect
  object Add extends Effect[(Key, SinkCommand), Throwable, Unit]

  val compose: URLayer[Proxy, SinkCQRS] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new SinkCQRS {
        // Implement the `add` method to utilize the defined effect
        def add(id: Key, cmd: SinkCommand): Task[Unit] =
          proxy(Add, (id, cmd))
      }
    }
}

package com.youtoo
package source

import com.youtoo.cqrs.*
import com.youtoo.source.model.*
import zio.*
import zio.mock.*

object MockSourceCQRS extends Mock[SourceCQRS] {

  // Define each action as an effect
  object Add extends Effect[(Key, SourceCommand), Throwable, Unit]

  val compose: URLayer[Proxy, SourceCQRS] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new SourceCQRS {
        // Implement the `add` method to utilize the defined effect
        def add(id: Key, cmd: SourceCommand): Task[Unit] =
          proxy(Add, (id, cmd))
      }
    }
}

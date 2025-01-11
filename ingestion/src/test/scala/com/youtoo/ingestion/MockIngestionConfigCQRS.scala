package com.youtoo
package ingestion

import com.youtoo.ingestion.model.*
import zio.*
import zio.mock.*

object MockIngestionConfigCQRS extends Mock[IngestionConfigCQRS] {
  object Add extends Effect[(Key, IngestionConfigCommand), Throwable, Unit]

  val compose: URLayer[Proxy, IngestionConfigCQRS] = ZLayer {
    for {
      proxy <- ZIO.service[Proxy]
    } yield new IngestionConfigCQRS {
      def add(id: Key, cmd: IngestionConfigCommand): Task[Unit] = proxy(Add, (id, cmd))
    }
  }
}

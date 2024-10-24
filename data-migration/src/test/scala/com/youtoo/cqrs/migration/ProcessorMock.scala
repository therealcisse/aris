package com.youtoo.cqrs
package migration

import zio.stream.*
import zio.mock.*
import zio.*

object ProcessorMock extends Mock[DataMigration.Processor] {

  object Count extends Effect[Unit, Throwable, Long]
  object Load extends Stream[Unit, Throwable, Key]
  object Process extends Effect[Key, Throwable, Unit]

  val compose: URLayer[Proxy, DataMigration.Processor] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
        service <- withRuntime[Proxy, DataMigration.Processor] { rts =>
          ZIO.succeed {
            new DataMigration.Processor {
              def count(): Task[Long] = proxy(Count)

              def load(): ZStream[Any, Throwable, Key] = Unsafe.unsafe { implicit u =>
                val t1: ZIO[Any, Throwable, ZStream[Any, Throwable, Key]] = proxy(Load)
                rts.unsafe.run(t1).getOrThrowFiberFailure()
              }

              def process(key: Key): Task[Unit] = proxy(Process, key)
            }
          }
        }
      } yield service
    }
}

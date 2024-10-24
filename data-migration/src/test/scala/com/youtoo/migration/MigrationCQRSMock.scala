package com.youtoo
package migration

import zio.mock.*
import zio.*

import com.youtoo.migration.model.*

object MigrationCQRSMock extends Mock[MigrationCQRS] {

  object Add extends Effect[(Key, MigrationCommand), Throwable, Unit]
  object Load extends Effect[Key, Throwable, Option[Migration]]

  val compose: URLayer[Proxy, MigrationCQRS] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new MigrationCQRS {
        def add(id: Key, cmd: MigrationCommand): Task[Unit] = proxy(Add, id, cmd)
        def load(id: Key): Task[Option[Migration]] = proxy(Load, id)
      }
    }
}

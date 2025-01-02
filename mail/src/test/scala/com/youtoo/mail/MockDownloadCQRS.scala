package com.youtoo
package mail

import com.youtoo.cqrs.*
import com.youtoo.mail.model.*
import zio.*
import zio.mock.*

object MockDownloadCQRS extends Mock[DownloadCQRS] {

  object Add extends Effect[(Key, DownloadCommand), Throwable, Unit]

  val compose: URLayer[Proxy, DownloadCQRS] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new DownloadCQRS {
        def add(id: Key, cmd: DownloadCommand): Task[Unit] =
          proxy(Add, (id, cmd))
      }
    }
}

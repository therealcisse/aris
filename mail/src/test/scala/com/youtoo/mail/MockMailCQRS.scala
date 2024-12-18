package com.youtoo
package mail

import com.youtoo.cqrs.*
import com.youtoo.mail.model.*
import zio.*
import zio.mock.*

object MockMailCQRS extends Mock[MailCQRS] {

  object Add extends Effect[(Key, MailCommand), Throwable, Unit]

  val compose: URLayer[Proxy, MailCQRS] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new MailCQRS {
        def add(id: Key, cmd: MailCommand): Task[Unit] =
          proxy(Add, (id, cmd))
      }
    }
}

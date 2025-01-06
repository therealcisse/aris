package com.youtoo
package mail

import com.youtoo.mail.model.*
import zio.*
import zio.mock.*

object MockMailConfigCQRS extends Mock[MailConfigCQRS] {
  object Add extends Effect[(Key, MailConfigCommand), Throwable, Unit]

  val compose: URLayer[Proxy, MailConfigCQRS] = ZLayer {
    for {
      proxy <- ZIO.service[Proxy]
    } yield new MailConfigCQRS {
      def add(id: Key, cmd: MailConfigCommand): Task[Unit] = proxy(Add, (id, cmd))
    }
  }
}

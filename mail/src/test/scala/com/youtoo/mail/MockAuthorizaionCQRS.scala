package com.youtoo
package mail

import com.youtoo.cqrs.*
import com.youtoo.mail.model.*
import zio.*
import zio.mock.*

object MockAuthorizationCQRS extends Mock[AuthorizationCQRS] {

  object Add extends Effect[(Key, AuthorizationCommand), Throwable, Unit]

  val compose: URLayer[Proxy, AuthorizationCQRS] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new AuthorizationCQRS {
        def add(id: Key, cmd: AuthorizationCommand): Task[Unit] =
          proxy(Add, (id, cmd))
      }
    }
}

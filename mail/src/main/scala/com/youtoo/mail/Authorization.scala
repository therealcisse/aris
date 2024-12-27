package com.youtoo
package mail

import com.youtoo.mail.model.*

enum Authorization {
  case Pending()
  case Granted(token: TokenInfo, timestamp: Timestamp)
  case Revoked(timestamp: Timestamp)

}

object Authorization {
  import zio.schema.*

  given Schema[Authorization] = DeriveSchema.gen

  extension (auth: Authorization)
    inline def isGranted(): Boolean = auth match {
      case _: Authorization.Granted => true
      case _ => false
    }

}

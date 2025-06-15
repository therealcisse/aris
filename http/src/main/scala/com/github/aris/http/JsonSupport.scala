package com.github
package aris
package http

import zio.*
import zio.json.*
import zio.http.*

trait JsonSupport {
  extension (req: Request)
    def jsonBody[A: JsonDecoder](using Trace): Task[A] =
      req.body.asString.flatMap { str =>
        ZIO.fromEither(str.fromJson[A].left.map(new Exception(_)))
      }

  extension [A: JsonEncoder](a: A)
    def toJsonResponse: Response =
      Response.json(a.toJson)
}

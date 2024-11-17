package com.youtoo
package observability
package otel

import zio.*
import zio.prelude.*

object TracingConfig {

  type Endpoint = Endpoint.Type

  object Endpoint extends Newtype[String] {
    extension (a: Endpoint) inline def value: String = Endpoint.unwrap(a)

    given Config[Endpoint] = Config.string("endpoint").nested("observability", "tracing") map { case (url) =>
      Endpoint.wrap(url)

    }

  }

}

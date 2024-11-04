package com.youtoo
package ingestion
package service

import zio.mock.*
import zio.*
import com.youtoo.ingestion.model.*

object MockLocationProvision extends Mock[LocationProvision] {
  object GetFiles extends Effect[String, Throwable, List[IngestionFile]]

  val compose: URLayer[Proxy, LocationProvision] =
    ZLayer.fromFunction { (proxy: Proxy) =>
      new LocationProvision {
        def getFiles(path: String): Task[List[IngestionFile]] = proxy(GetFiles, path)
      }
    }
}

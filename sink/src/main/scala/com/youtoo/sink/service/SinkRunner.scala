package com.youtoo
package sink
package service

import zio.*
import zio.prelude.*

trait SinkRunner[T] {
  def process(data: NonEmptyList[T]): Task[Unit]
}

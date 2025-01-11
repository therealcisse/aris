package com.youtoo
package source
package service

import zio.*
import zio.stream.*

trait SourceReader[O, T] {
  def stream: ZStream[Any, Throwable, T]
  def commit(offset: O): Task[Unit]
}

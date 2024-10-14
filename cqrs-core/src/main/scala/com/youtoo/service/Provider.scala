package com.youtoo.cqrs
package service

import zio.*

transparent trait Provider[T] {
  def load(id: Key): Task[Option[T]]

}

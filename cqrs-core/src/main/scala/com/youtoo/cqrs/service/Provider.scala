package com.youtoo
package cqrs
package service

import zio.*

transparent trait Provider[T] {
  def load(id: Key): Task[Option[T]]

}

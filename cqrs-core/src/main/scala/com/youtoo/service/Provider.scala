package com.youtoo.cqrs
package service

import com.youtoo.cqrs.domain.*

import zio.*

transparent trait Provider[T] {
  def load(id: Key): Task[Option[T]]

}

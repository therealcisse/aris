package com.youtoo.cqrs
package service

import com.youtoo.cqrs.domain.*

import zio.*

transparent trait Checkpointer[T] {
  def save(o: T, version: Version): Task[Unit]

}

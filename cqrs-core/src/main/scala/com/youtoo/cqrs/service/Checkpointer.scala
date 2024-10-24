package com.youtoo
package cqrs
package service

import zio.*

transparent trait Checkpointer[T] {
  def save(o: T): Task[Unit]

}

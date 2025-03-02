package com.github
package aris
package service

import zio.*

trait IdGenerator {
  def gen(): UIO[Long]
}

object IdGenerator {
  class IdGeneratorLive() extends IdGenerator {
    private val sf = SnowflakeIdGenerator(0)
    def gen(): UIO[Long] = ZIO.succeed(sf.nextId())
  }

  val ref: FiberRef[IdGenerator] =
    Unsafe.unsafe { implicit unsafe =>
      FiberRef.unsafe.make(new IdGeneratorLive())

    }

  def gen(): UIO[Long] = ref.get.flatMap(_.gen())
}

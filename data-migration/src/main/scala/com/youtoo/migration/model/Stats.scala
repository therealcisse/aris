package com.youtoo
package migration
package model

import cats.implicits.*

import zio.*

import zio.schema.*

case class Stats(processing: Set[Key], processed: Set[Key], failed: Set[Key])

object Stats {
  given Schema[Stats] = DeriveSchema.gen

  def empty: Stats = Stats(Set(), Set(), Set())
}

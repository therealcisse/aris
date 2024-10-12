package com.youtoo.cqrs
package store

import com.youtoo.cqrs.domain.*

import zio.*

transparent trait AggregateStore {
  def readAggregate(id: Key): Task[Option[Aggregate]]
  def save(id: Key, agg: Aggregate): Task[Unit]
}

package com.github
package aris

import zio.prelude.*

import cats.implicits.*

enum PersistenceQuery {
  case Condition(
    namespace: Option[Namespace]
  )
  case any(condition: PersistenceQuery.Condition, more: PersistenceQuery.Condition*)
  case forall(condition: PersistenceQuery, more: PersistenceQuery*)
}

object PersistenceQuery {
  inline def condition(
    namespace: Option[Namespace] = None
  ): PersistenceQuery.Condition = PersistenceQuery.Condition(
    namespace
  )

  inline def ns(value: Namespace): PersistenceQuery.Condition = condition(namespace = value.some)
  inline def anyNamespace(value: Namespace, more: Namespace*): PersistenceQuery.any =
    PersistenceQuery.any(ns(value), more.map(ns(_))*)


}

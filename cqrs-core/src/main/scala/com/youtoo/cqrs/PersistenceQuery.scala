package com.youtoo
package cqrs

import zio.prelude.*

import cats.implicits.*

enum PersistenceQuery {
  case Condition(
    namespace: Option[Namespace],
    props: Option[NonEmptyList[EventProperty]],
    hierarchy: Option[Hierarchy],
    reference: Option[ReferenceKey],
  )
  case any(condition: PersistenceQuery.Condition, more: PersistenceQuery.Condition*)
  case forall(condition: PersistenceQuery, more: PersistenceQuery*)
}

object PersistenceQuery {
  inline def condition(
    namespace: Option[Namespace] = None,
    props: Option[NonEmptyList[EventProperty]] = None,
    hierarchy: Option[Hierarchy] = None,
    reference: Option[ReferenceKey] = None,
  ): PersistenceQuery.Condition = PersistenceQuery.Condition(
    namespace,
    props,
    hierarchy,
    reference,
  )

  inline def ns(value: Namespace): PersistenceQuery.Condition = condition(namespace = value.some)
  inline def anyNamespace(value: Namespace, more: Namespace*): PersistenceQuery.any =
    PersistenceQuery.any(ns(value), more.map(ns(_))*)
  inline def props(value: EventProperty, more: EventProperty*): PersistenceQuery.Condition =
    condition(props = NonEmptyList(value, more*).some)
  inline def hierarchy(value: Hierarchy): PersistenceQuery.Condition = condition(hierarchy = value.some)
  inline def child(parentId: Key): PersistenceQuery.Condition = condition(hierarchy = Hierarchy.Child(parentId).some)
  inline def descendant(grandParentId: Key, parentId: Key): PersistenceQuery.Condition =
    condition(hierarchy = Hierarchy.Descendant(grandParentId, parentId).some)
  inline def grandChild(grandParentId: Key): PersistenceQuery.Condition =
    condition(hierarchy = Hierarchy.GrandChild(grandParentId).some)
  inline def reference(value: ReferenceKey): PersistenceQuery.Condition = condition(reference = value.some)

}

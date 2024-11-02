package com.youtoo
package cqrs

enum Hierarchy {
  case Child(parentId: Key)
  case GrandChild(grandParentId: Key)
  case Descendant(grandParentId: Key, parentId: Key)
}

object Hierarchy {}

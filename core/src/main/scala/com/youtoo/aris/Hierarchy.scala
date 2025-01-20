package com.youtoo
package aris

enum Hierarchy {
  case Child(parentId: Key)
  case GrandChild(grandParentId: Key)
  case Descendant(grandParentId: Key, parentId: Key)
}

object Hierarchy {}

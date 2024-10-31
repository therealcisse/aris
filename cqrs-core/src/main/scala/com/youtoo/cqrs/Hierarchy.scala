package com.youtoo
package cqrs

enum Hierarchy {
  case Child(parentId: Key)
  case Descendant(grandParentId: Key, parentId: Key)
}
